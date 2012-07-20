package com.heroku.play.api.libs.caching

import play.api.libs.concurrent.{Akka, Redeemable, Promise}
import akka.util.Duration
import com.heroku.play.api.libs.redis.RedisService
import org.slf4j.LoggerFactory
import org.codehaus.jackson.map.DeserializationConfig
import com.codahale.jerkson.Json
import redis.clients.jedis.Jedis
import akka.util.duration._
import play.api.Play.current

trait CachingService[C] {

  def asyncCached[T](cache: String, key: String, expiration: Duration = 5 minutes, onHit: C => Unit = cOnHit(_))(block: => Promise[T])(implicit m: Manifest[T], context: C): Promise[T]

  def cached[T](cache: String, key: String, expiration: Duration = 5 minutes, onHit: C => Unit = cOnHit(_))(block: => T)(implicit m: Manifest[T], context: C): T

  def cOnHit(c: C){}
}

object LaxJson extends Json {
  //We use this object to deserialize objects from heroku.jar which internally uses the same settings
  mapper.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

trait RedisCachingServiceNoContext extends RedisCachingService[Unit] {

  implicit val unitCtx = ()

}


trait RedisCachingService[C] extends CachingService[C] {
  private val log = LoggerFactory.getLogger(classOf[CachingService[_]])

  def redisService: RedisService

  def cacheName: String

  def cacheKey(cache: String, key: String): String = (cacheName + "-" + cache + "-key-" + key)

  def fromSource[T](redisKey: String, expiration: Duration, promise: Redeemable[T], block: => Promise[T])(implicit m: Manifest[T]) = {
    val blockPromise = block
    blockPromise.extend {
      bp => bp.value.fold(be => promise.throwing(be), {
        bs =>
          promise.redeem(bs)
          redisService.withRedis {
            redis =>
              calculateAndSave(redis, redisKey, expiration, bs)
              ()
          }
      })
    }
  }

  def asyncCached[T](cache: String, key: String, expiration: Duration = 5 minutes, onHit: C => Unit)(block: => Promise[T])(implicit m: Manifest[T], context: C): Promise[T] = {
    val redisKey = cacheKey(cache, key)
    val promise = Promise[T]()
    Akka.future {
      redisService.withRedis {
        redis => get[T](redis, redisKey)
      }
    }.extend {
      p => p.value.fold(e => {
        log.error("exception reading from cache, going to source", e)
        fromSource(redisKey, expiration, promise, block)
      }, _ match {
        case Some(t) => {
          log.debug("async cache hit for {}", redisKey)
          onHit
          promise.redeem(t)
        }
        case None => {
          log.debug("async cache miss for {}", redisKey)

          fromSource(redisKey, expiration, promise, block)
        }
      })
    }
    promise
  }

  def toJson[A](ref: A): String = LaxJson.generate(ref)

  def fromJson[A](json: String)(implicit m: Manifest[A]) = LaxJson.parse[A](json)

  def calculateAndSave[T](jedis: Jedis, redisKey: String, expiration: Duration, block: => T): T = {
    val result = block
    try {
      val json = toJson(result)
      jedis.setex(redisKey, expiration.toSeconds.toInt, json)
      result
    } catch {
      case e: Exception =>
        log.error("exception while trying to write to redis cache key:" + redisKey, e)
        result
    }
  }

  def get[T](jedis: Jedis, redisKey: String)(implicit m: Manifest[T]): Option[T] = {
    try {
      Option(jedis.get(redisKey)).map(json => fromJson[T](json))
    } catch {
      case e: Exception => //Purge the cache on any exception
        try {
          log.error("exception while trying to read from redis key:" + redisKey, e)
          jedis.del(redisKey)
          None
        } catch {
          case e: Exception =>
            log.error("exception while trying to purge to redis cache key:" + redisKey, e)
            None
        }
    }
  }


  def cached[T](cache: String, key: String, expiration: Duration = 5 minutes, onHit: C => Unit)(block: => T)(implicit m: Manifest[T], context: C): T = {
    val redisKey = cacheKey(cache, key)
    try {
      redisService.withRedis {
        redis =>
          get(redis, redisKey) match {
            case Some(t) =>
              log.info("cache hit for {}", redisKey)
              onHit
              t
            case None =>
              log.info("cache miss for {}", redisKey)
              calculateAndSave(redis, redisKey, expiration, block)
          }
      }
    }
  }

}
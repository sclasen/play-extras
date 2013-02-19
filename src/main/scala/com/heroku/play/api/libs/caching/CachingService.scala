package com.heroku.play.api.libs.caching

import com.heroku.play.api.libs.redis.RedisService
import concurrent.{ Future, Promise, future, promise }
import org.slf4j.LoggerFactory
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import redis.clients.jedis.Jedis
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

trait CachingService[C] {

  def asyncUncache(cache: String, key: String)

  def asyncCached[T](cache: String, key: String, expiration: Duration = 5 minutes, onHit: C => Unit = defaultOnHit(_))(block: => Future[T])(implicit m: Manifest[T], context: C, f: Format[T]): Future[T]

  def uncache(cache: String, key: String)

  def cached[T](cache: String, key: String, expiration: Duration = 5 minutes, onHit: C => Unit = defaultOnHit(_))(block: => T)(implicit m: Manifest[T], context: C, f: Format[T]): T

  def defaultOnHit(c: C) {}
}

trait RedisCachingServiceNoContext extends RedisCachingService[Unit] {

  implicit val unitCtx = ()

}

trait RedisCachingService[C] extends CachingService[C] {
  private val log = LoggerFactory.getLogger(classOf[CachingService[_]])

  def redisService: RedisService

  def cacheName: String

  def cacheKey(cache: String, key: String): String = (cacheName + "-" + cache + "-key-" + key)

  def fromSource[T](redisKey: String, expiration: Duration, promise: Promise[T], block: => Future[T])(implicit m: Manifest[T], f: Format[T]) = {
    val blockPromise = block
    blockPromise.onComplete {
      case Success(t) =>
        promise.success(t)
        redisService.withRedis {
          redis =>
            calculateAndSave(redis, redisKey, expiration, t)
            ()
        }
      case Failure(e) => promise.failure(e)
    }
  }

  def asyncUncache(cache: String, key: String) {
    future {
      redisService.withRedis {
        redis => redis.del(cacheKey(cache, key))
      }
    }
    ()
  }

  override def asyncCached[T](cache: String, key: String, expiration: Duration = 5 minutes, onHit: C => Unit)(block: => Future[T])(implicit m: Manifest[T], context: C, f: Format[T]): Future[T] = {
    val redisKey = cacheKey(cache, key)
    val pr = promise[T]
    future {
      redisService.withRedis {
        redis => get[T](redis, redisKey)
      }
    }.onComplete {
      case Success(Some(t)) => {
        log.debug("async cache hit for {}", redisKey)
        onHit(context)
        pr.success(t)
      }
      case Success(None) => {
        log.debug("async cache miss for {}", redisKey)
        fromSource(redisKey, expiration, pr, block)
      }
      case Failure(e) => {
        log.error("exception reading from cache, going to source", e)
        fromSource(redisKey, expiration, pr, block)
      }
    }
    pr.future
  }

  def toJson[A](ref: A)(implicit f: Format[A]): String = Json.stringify(f.writes(ref))

  def fromJson[A](json: String)(implicit m: Manifest[A], f: Format[A]) = Json.fromJson(Json.parse(json)).get

  def calculateAndSave[T](jedis: Jedis, redisKey: String, expiration: Duration, block: => T)(implicit f: Format[T]): T = {
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

  def get[T](jedis: Jedis, redisKey: String)(implicit m: Manifest[T], f: Format[T]): Option[T] = {
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

  def uncache(cache: String, key: String) {
    redisService.withRedis {
      redis => redis.del(cacheKey(cache, key))
    }
    ()
  }

  def cached[T](cache: String, key: String, expiration: Duration = 5 minutes, onHit: C => Unit)(block: => T)(implicit m: Manifest[T], context: C, f: Format[T]): T = {
    val redisKey = cacheKey(cache, key)
    try {
      redisService.withRedis {
        redis =>
          get(redis, redisKey) match {
            case Some(t) =>
              log.info("cache hit for {}", redisKey)
              onHit(context)
              t
            case None =>
              log.info("cache miss for {}", redisKey)
              calculateAndSave(redis, redisKey, expiration, block)
          }
      }
    } catch {
      case e: Exception => {
        log.error("exception in cached() going to source directly", e)
        block
      }
    }
  }

}
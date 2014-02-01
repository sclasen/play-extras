package com.heroku.play.api.libs.redis

import java.net.URI
import org.slf4j.LoggerFactory
import redis.clients.jedis._
import scala.Some
import org.apache.commons.pool.impl.GenericObjectPool.Config
import java.util.concurrent.atomic.AtomicInteger
import play.api.Play.current

object RedisService {
  lazy val redisUrl = current.configuration.getString(configName).map(uri => new URI(uri))
    .getOrElse(sys.env.get("REDISTOGO_URL").map(uri => new URI(uri)).getOrElse(sys.error("unable to load redis url from %s config or %s env".format(configName, "REDISTOGO_URL"))))
  /*
  use a config like this

  redis.service.url=${?REDISTOGO_URL}
  redis.service.url=${?REDISGREEN_URL}
  ...etc...
  then you can change addon providers in an outage by adding a new provider and dropping the down one.
   */
  val configName = "redis.service.url"

  val subscribeThreadCounter = new AtomicInteger(0)

  def apply(): RedisService = new RedisService(redisUrl)

  def apply(uri: URI) = new RedisService(uri)
}

class RedisService(val redisUrl: URI) {

  val log = LoggerFactory.getLogger("Redis")

  val redisPassword: Option[String] = Option(redisUrl.getUserInfo).map(_.split(":").apply(1))

  lazy val redisPool: JedisPool = createRedisPool()

  def redisConnection(): Jedis = {
    val redis = new Jedis(redisUrl.getHost, redisUrl.getPort)
    for (p <- redisPassword) {
      redis.auth(p)
    }
    log.info("redisConnection()")
    redis
  }

  def subscribe(redis: Jedis, handler: (String, String) => Unit, exceptionHandler: PartialFunction[Throwable, Unit], channels: String*) {
    val t = new Thread(new Runnable {
      def run() {
        try {
          redis.subscribe(new JedisPubSub() {
            def onMessage(channel: String, message: String) {
              log.info("Redis.subscribe.onMessage {} {}", channel, message)
              handler(channel, message)
            }

            def onPMessage(pattern: String, channel: String, message: String) {}

            def onSubscribe(channel: String, subscribedChannels: Int) {}

            def onUnsubscribe(channel: String, subscribedChannels: Int) {}

            def onPUnsubscribe(pattern: String, subscribedChannels: Int) {}

            def onPSubscribe(pattern: String, subscribedChannels: Int) {}
          }, channels: _*)
        } catch {
          exceptionHandler
        }
      }
    }, "redis subscription thread:" + RedisService.subscribeThreadCounter.incrementAndGet())
    t.setDaemon(true)
    t.start()

  }

  def createRedisPool(): JedisPool = {
    val config = new Config()
    config.testOnBorrow = true
    redisPassword match {
      case Some(password) => new JedisPool(config, redisUrl.getHost, redisUrl.getPort, Protocol.DEFAULT_TIMEOUT, password)
      case None => new JedisPool(config, redisUrl.getHost, redisUrl.getPort, Protocol.DEFAULT_TIMEOUT, null)
    }
  }

  def withRedis[T](thunk: Jedis => T): T = {
    val resource = redisPool.getResource
    try {
      val t = thunk(resource)
      redisPool.returnResource(resource.asInstanceOf[BinaryJedis])
      t
    } catch {
      case e: Exception =>
        redisPool.returnBrokenResource(resource.asInstanceOf[BinaryJedis])
        throw e
      case nsm : NoSuchMethodError =>
        log.error("No Such Method error caught in RedisService.withRedis, is your redis dependency in sync with play-extras?")
        throw new RuntimeException(nsm)
    }
  }

}

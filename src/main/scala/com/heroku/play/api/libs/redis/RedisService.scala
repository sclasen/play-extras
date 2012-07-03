package com.heroku.play.api.libs.redis


import java.net.URI
import org.slf4j.LoggerFactory
import redis.clients.jedis._
import scala.Some
import org.apache.commons.pool.impl.GenericObjectPool.Config


object RedisService {
  lazy val redisUrl = new URI(sys.env("REDISTOGO_URL"))

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
    }, "redis subscription thread")
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
    }
  }

}

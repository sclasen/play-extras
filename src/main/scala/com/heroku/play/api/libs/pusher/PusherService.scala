package com.heroku.play.api.libs.pusher

package services


import com.codahale.jerkson.Json
import java.math.BigInteger
import java.net.URI
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory
import play.api.Play._
import play.api.http.HeaderNames._
import play.api.libs.ws.{Response, WS}
import play.api.libs.concurrent.Promise
import com.heroku.play.api.mvc.ToJson


//play2/scala translation of https://github.com/regisbamba/Play-Pusher

object PusherUtil {

  def byteArrayToString(data: Array[Byte]): String = {
    val bigInteger = new BigInteger(1, data);
    var hash = bigInteger.toString(16);
    while (hash.length() < 32) {
      hash = "0" + hash;
    }
    hash;
  }

  def md5(string: String): String = {
    val bytesOfMessage = string.getBytes("UTF-8");
    val md = MessageDigest.getInstance("MD5");
    val digest = md.digest(bytesOfMessage);
    byteArrayToString(digest);
  }

  def sha256(toSign: String, secret: String): String = {
    val signingKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");

    val mac = Mac.getInstance("HmacSHA256");
    mac.init(signingKey);

    var digest = mac.doFinal(toSign.getBytes("UTF-8"));
    digest = mac.doFinal(toSign.getBytes()); //??WTF

    val bigInteger = new BigInteger(1, digest);
    String.format("%0" + (digest.length << 1) + "x", bigInteger);
  }

}

case class AuthData(auth: String, channel_data: Option[String] = None)

case class BasicUserInfo(name: String)

case class PresenceChannelData(user_id: String, user_info: AnyRef)

case class PusherConfig(id: String, key: String, secret: String)

object PusherService {
  def apply(): PusherService = {
    val url = current.configuration.getString("pusher.url")
    url.map {
      u =>
        val uri = new URI(u)
        apply(uri)
    }.getOrElse(sys.error("pusher.url not found in config"))
  }

  def apply(uri: URI): PusherService = {
    val keySecret = uri.getUserInfo.split(":")
    val appId = uri.getPath.substring(uri.getPath.lastIndexOf("/") + 1)
    new PusherService(PusherConfig(appId, keySecret(0), keySecret(1)))
  }
}

class PusherService(val config: PusherConfig) {
  val log = LoggerFactory.getLogger(classOf[PusherService])

  private final val host: String = "api.pusherapp.com"

  def trigger(channel: String, event: String, message: String, socketId: String = null): Promise[Response] = {
    val path: String = "/apps/" + config.id + "/events"
    val pusherMessage = PusherMessage(event, message, channel).json
    val query: String = "auth_key=" + config.key + "&auth_timestamp=" + (System.currentTimeMillis / 1000) + "&auth_version=1.0" + "&body_md5=" + PusherUtil.md5(pusherMessage)
    val signature: String = PusherUtil.sha256("POST\n" + path + "\n" + query, config.secret)
    val uri: String = "http://" + host + path + "?" + query + "&auth_signature=" + signature
    WS.url(uri).withHeaders(CONTENT_TYPE -> "application/json").post(pusherMessage)
  }

  def createAuthString(socketId: String, channel: String): String = {
    val signature: String = PusherUtil.sha256((socketId + ":" + channel), config.secret)
    Json.generate(AuthData(config.key + ":" + signature))
  }

  def createAuthString(socketId: String, channel: String, channelData: PresenceChannelData): String = {
    var jsonChannelData: String = Json.generate(channelData)
    var signature = PusherUtil.sha256((socketId + ":" + channel + ":" + jsonChannelData), config.secret)
    Json.generate(AuthData(config.key + ":" + signature, Some(jsonChannelData)))
  }


  def verifyWebhook(key: String, signature: String, signedBody: String): Boolean = {
    log.debug("verifyWebhook key {} config key {} sig {} body {}", Array[Object](key, config.key, signature, signedBody))
    if (key == config.key) {
      val sha: String = PusherUtil.sha256(signedBody, config.secret)
      log.debug("sha : {}", sha)
      sha == signature
    } else false
  }

  def getActiveChannels(): Promise[Set[String]] = {
    val path: String = "/apps/" + config.id + "/channels"
    val query: String = "auth_key=" + config.key + "&auth_timestamp=" + (System.currentTimeMillis / 1000) + "&auth_version=1.0"
    val signature: String = PusherUtil.sha256("GET\n" + path + "\n" + query, config.secret)
    val uri: String = "http://" + host + path + "?" + query + "&auth_signature=" + signature
    WS.url(uri).get().map {
      resp =>
        if (resp.status != 200) log.error(resp.body)
        Json.parse[Map[String, Map[String, Any]]](resp.body).apply("channels").keySet
    }
  }


}

case class PusherMessage(name: String, data: String, channel: Option[String], channels: Option[Array[String]], socket_id: Option[String]) extends ToJson

object PusherMessage {
  def apply(name: String, data: String, channel: String): PusherMessage = PusherMessage(name, data, Some(channel), None, None)
}


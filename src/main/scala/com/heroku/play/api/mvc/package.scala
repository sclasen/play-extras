package com.heroku.play.api.libs.mvc

import java.net.URLEncoder
import com.ning.http.util.Base64
import org.jboss.netty.util.CharsetUtil.UTF_8

object BasicAuth {
  def apply(apiKey: String): String = apply("", apiKey)

  def apply(user: String, password: String): String = "Basic " + Base64.encode((user + ":" + password).getBytes(UTF_8))

  def extractKey(headerValue: String): Option[String] = {
    val decoded = decode(headerValue)
    if (decoded.startsWith(":")) Some(decoded.substring(1))
    else None
  }

  def extractUserPass(headerValue: String): Option[(String, String)] = {
    decode(headerValue).split(":").toList match {
      case u :: p :: Nil => Some(u -> p)
      case _ => None
    }
  }

  private def decode(headerValue: String): String = {
    val auth = headerValue.replaceFirst("Basic ", "")
    new String(new sun.misc.BASE64Decoder().decodeBuffer(auth), UTF_8)
  }
}

object UrlEncode {
  def apply(toEncode: String) = URLEncoder.encode(toEncode, UTF_8.displayName())
}


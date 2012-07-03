package com.heroku.play.api.mvc

import collection.Map
import play.api.mvc.Controller
import com.codahale.jerkson.Json
import play.api.libs.json._
import scalaz._
import Scalaz._
import play.api.libs.json.JsBoolean
import play.api.mvc.SimpleResult
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import play.api.libs.json.JsNumber
import scala.Some


trait JsonAPI extends Controller {
  val cacheableJsonHeaders = CONTENT_TYPE -> JSON
  val jsonHeaders = Seq(CONTENT_TYPE -> JSON, CACHE_CONTROL -> "no-cache")

  def json(status: Status, content: ToJson): SimpleResult[String] = json(status, content.json)

  def json(status: Status, content: List[_]): SimpleResult[String] = json(status, Json.generate(content))

  def json(status: Status, content: String): SimpleResult[String] = {
    status(content).withHeaders(jsonHeaders: _*)
  }

  def require[T](js: JsValue, field: String)(implicit fjs: Reads[T]): ValidationNEL[String, T] = {
    (js \ field).asOpt[T].map(t => t.success).getOrElse((field + " was not found").failNel)
  }

  def require[T, S](js: JsValue, field: String, convert: T => Option[S])(implicit fjs: Reads[T]): ValidationNEL[String, S] = {
    require[T](js, field).map(v => convert(v)).flatMap {
      case Some(s) => s.success
      case None => ("Unable to convert field " + field + " to valid value").failNel
    }
  }

  def opt[T](js: JsValue, field: String)(implicit fjs: Reads[T]): ValidationNEL[String, Option[T]] = {
    (js \ field).asOpt[T].success
  }

  def str(js: JsValue, field: String)(implicit fjs: Reads[String]): ValidationNEL[String, String] = {
    require[String](js, field)
  }

  def long(js: JsValue, field: String)(implicit fjs: Reads[Long]): ValidationNEL[String, Long] = {
    require[Long](js, field)
  }


  def err(es: NonEmptyList[String]): Error = Error(es.list.reduce(_ + ", " + _))

  def map(js: JsValue, field: String)(implicit fjs: Reads[JsObject]): ValidationNEL[String, Map[String, Any]] = {
    (js \ field).asOpt[JsObject].map {
      ob =>
        val map = ob.value.map {
          case (key, num: JsNumber) => key -> num.value
          case (key, s: JsString) => key -> s.value
          case (key, b: JsBoolean) => key -> b.value.toString
          case (key, JsNull) => key -> null
          case (key, _) => key -> "invalid value"
        }
        map.success
    }.getOrElse(("unable to convert " + field + "to map").failNel)
  }
}

trait ToJson {
  def json: String = Json.generate(this)
}

case class Error(error_message: String) extends ToJson

case class Info(message: String) extends ToJson





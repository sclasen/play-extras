package com.heroku.play.api.libs

import play.api.libs.json._
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNumber
import play.api.http.Writeable._
import play.api.http.Writeable

package object json {

  val wError: Writes[Error] = Writes[Error](e => Json.obj("error_message" -> e.error_message))
  val rError: Reads[Error] = Json.reads[Error]
  val wInfo: Writes[Info] = Writes[Info](e => Json.obj("message" -> e.message))
  val rInfo: Reads[Info] = Json.reads[Info]

  implicit val fError: Format[Error] = Format(rError, wError)
  implicit val fInfo: Format[Info] = Format(rInfo, wInfo)
  //implicit val wrError: Writeable[Error] = writeableOf_JsValue.map(wError.writes)
  //implicit val wrInfo: Writeable[Info] = writeableOf_JsValue.map(wInfo.writes)
  implicit val wjs = Writeable.writeableOf_JsValue

  implicit def writesToWriteable[A](implicit wa: Writes[A]): Writeable[A] = wjs.map(wa.writes)

  implicit val rMapStringAny: Reads[Map[String, Any]] = Reads[Map[String, Any]] {
    js =>
      JsSuccess {
        js.as[JsObject].fields.map {
          case (f, JsString(s)) => f -> s
          case (f, JsBoolean(b)) => f -> b
          case (f, JsNumber(n)) => f -> n
          case (f, JsNull) => f -> null
          case (f, JsArray(a)) => f -> a.toString()
          case (f, JsObject(o)) => f -> o.toString()
          case (f, JsUndefined(e)) => f -> e
        }.toMap
      }
  }

  implicit val wMapStringAny: Writes[Map[String, Any]] = Writes[Map[String, Any]] {
    m =>
      JsObject {
        m.toSeq.map {
          case (f, s: String) => f -> JsString(s)
          case (f, b: Boolean) => f -> JsBoolean(b)
          case (f, null) => f -> JsNull
          case (f, i: Int) => f -> JsNumber(BigDecimal(i))
          case (f, l: Long) => f -> JsNumber(BigDecimal(l))
          case (f, _) => f -> JsUndefined("unexpected value writing Map[String,Any]")
        }
      }
  }

  implicit val fMapStringAny: Format[Map[String, Any]] = Format[Map[String, Any]](rMapStringAny, wMapStringAny)

  implicit class RichJsError(val e: JsError) extends AnyVal {
    def toError: Error = Error {
      e.errors.foldLeft(new StringBuilder) {
        case (res, (path, errors)) =>
          res.append(path.toString()).append(" had errors ")
          errors.foldLeft(res) {
            case (r, e) => r.append(e.message).append(" ")
          }
      }.toString()
    }
  }

}

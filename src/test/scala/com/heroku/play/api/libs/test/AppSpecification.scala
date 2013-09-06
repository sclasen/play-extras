package com.heroku.play.api.libs.test

import com.heroku.play.api.libs.json.Formats._
import com.heroku.play.api.libs.mvc.BasicAuth
import org.specs2.matcher.{ MatchResult, Expectable, Matcher }
import org.specs2.mutable.Specification
import play.api.db.BoneCPPlugin
import play.api.libs.ws.Response
import play.api.libs.ws.WS
import play.api.test.FakeApplication
import play.api.test.Helpers._
import play.api.http.{ Writeable, ContentTypeOf }
import play.api.libs.json.{ Writes, JsValue }

class AppSpecification extends Specification {

  val serverUrl = sys.env.get("TEST_SERVER_URL").getOrElse("http://localhost:9000")
  implicit def jsonType[T](implicit wt: Writes[T], wj: Writes[JsValue], w: Writeable[JsValue]): ContentTypeOf[T] = ContentTypeOf(ContentTypeOf.contentTypeOf_JsValue.mimeType)

  def haveStatus(status: Int*): Matcher[Response] = new Matcher[Response] {
    def apply[S <: Response](t: Expectable[S]): MatchResult[S] = {
      def bodyOrError[S <: Response](t: Expectable[S]) = {
        val trace = Thread.currentThread().getStackTrace()(11)
        /*
        11 is the number of frames to get the specification ^ thats executing. Determined by
        val x = new Exception();
        x.printStackTrace
         */
        val loc = "(%s.%d)".format(trace.getFileName, trace.getLineNumber)
        val message = if (t.value.body == null || t.value.body.size == 0) "Response was empty, status was:" + t.value.status + " not:" + status.map(_.toString).reduceLeft(_ + ", " + _)
        else "status was:" + t.value.status + "status was not:" + status.map(_.toString).reduceLeft(_ + ", " + _) + "   " + t.value.body
        " " + message + " " + loc + """  <--- """
      }
      result(status.contains(t.value.status), "status was:" + status, bodyOrError(t), t)
    }

  }

  def runningApp[T](block: => T): T = {
    testApp
    block
  }

  def api(auth: String, path: Any*) = {
    val url = path.foldLeft(new StringBuilder(serverUrl))(_.append(_)).toString()
    WS.url(url).withHeaders(AUTHORIZATION -> auth)
  }

  def jsonAPI(auth: String, path: Any*) = api(auth, path: _*).withHeaders(CONTENT_TYPE -> "application/json")

  def jsonAPIPost(auth: String, map: Map[String, Any], path: Any*) = jsonAPI(auth, path: _*).post(map)

  def jsonAPIPut(auth: String, map: Map[String, Any], path: Any*) = jsonAPI(auth, path: _*).put(map)

  def asBasic(key: String): String = BasicAuth(key)

  def require(key: String): String = sys.env.get(key).getOrElse(sys.error(key + " not found in env"))

  def testApp: FakeApplication = AppSpecification._testApp

}

object AppSpecification {

  lazy val _testApp = {
    val app = FakeApplication(additionalPlugins = Seq(classOf[TestDBPlugin].getName), withoutPlugins = Seq(classOf[BoneCPPlugin].getName))
    play.api.Play.start(app)
    app
  }

}
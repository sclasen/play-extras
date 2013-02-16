package com.heroku.play.api.libs.test

import org.specs2.mutable.Specification
import org.specs2.matcher.{ MatchResult, Expectable, Matcher }
import play.api.libs.ws.Response
import play.api.test.FakeApplication

class AppSpecification extends Specification {
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
    AppSpecification._testApp
    block
  }

}

object AppSpecification {

  lazy val _testApp = {
    val app = FakeApplication(additionalPlugins = Seq("com.heroku.play.api.lib.test.TestDBPlugin"), withoutPlugins = Seq("play.api.db.BoneCPPlugin"))
    play.api.Play.start(app)
    app
  }

}
package com.heroku.play.api.libs.pusher

import scala.concurrent.duration._
import concurrent.Await
import org.specs2.matcher.MatchResult
import play.api.test.Helpers._
import play.api.test.FakeApplication
import com.heroku.play.api.libs.pusher.services.PusherService
import org.specs2.mutable.Specification
import play.api.libs.ws.Response
import java.util.concurrent.TimeUnit

class PusherServiceSpec extends Specification {

  val config = Map("pusher.url" -> sys.env("PUSHER_URL"))

  "PusherService" should {
    "successfully post events" in withPusher {
      svc =>
        val response: Response = Await.result(svc.trigger("testing", "testing", "123"), Duration(10, TimeUnit.SECONDS))
        println(response.body)
        response.status mustEqual 200
    }
  }

  def withPusher(block: PusherService => MatchResult[Any]): MatchResult[Any] = running(FakeApplication(additionalConfiguration = config, withoutPlugins = Seq("play.api.cache.EhCachePlugin"))) {
    val svc = PusherService()
    block(svc)
  }

}

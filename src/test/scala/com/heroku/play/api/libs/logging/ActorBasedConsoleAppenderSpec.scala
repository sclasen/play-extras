package com.heroku.play.api.libs.logging

import org.specs2.mutable.Specification

class ActorBasedConsoleAppenderSpec extends Specification {

  "ActorBasedAppender" should {

    "start up correctly " in {
      ActorBasedConsoleAppender.consoleActor.isTerminated mustNotEqual (true)
    }
  }

}

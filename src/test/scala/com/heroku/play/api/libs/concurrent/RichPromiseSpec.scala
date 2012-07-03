package com.heroku.play.api.libs.concurrent

import play.api.libs.concurrent.{Promise, PurePromise}
import org.specs2.mutable.Specification
import RichPromise._
import play.api.test.Helpers._


class RichPromiseSpec extends Specification {


  "RichPromise rescueWith" should {
    "return a successful tired value" in {
      val rescue: Promise[Option[Int]] = PurePromise(1) rescueWith PurePromise(2)
      val calc = await(rescue)
      calc.isDefined mustEqual true
      calc.get mustEqual 1
    }

    "return a succesful rescue value " in {
      val rescue: Promise[Option[Int]] = PurePromise[Int](sys.error("try failed")) rescueWith PurePromise(2)
      val calc = await(rescue)
      calc.isDefined mustEqual true
      calc.get mustEqual 2
    }

    "return a none if both fail" in {
      val rescue: Promise[Option[Int]] = PurePromise[Int](sys.error("try failed")) rescueWith PurePromise[Int](sys.error("rescue failed"))
      val calc = await(rescue)
      calc.isDefined mustEqual false
    }

    "not execute the rescue on success" in {
      @volatile var x = 0
      val rescue: Promise[Option[Int]] = PurePromise(1) rescueWith {
        if (true) {
          x = 1
          failure("Shouldnt exec")
        }
        PurePromise(2)
      }
      val calc = await(rescue)
      calc.isDefined mustEqual true
      calc.get mustEqual 1
      x mustEqual 0
    }
  }

  "RichPromise toOpt" should {
    "return A Some(success) when successful" in {
      val toopt: Promise[Option[Int]] = PurePromise(1).asOpt("fail msg")
      val calc = await(toopt)
      calc.isDefined mustEqual true
    }

    "return a None when a failure occurs" in {
      val rescue: Promise[Option[Int]] = PurePromise[Int](sys.error("try failed")).asOpt("fail msg")
      val calc = await(rescue)
      calc.isDefined mustEqual false
    }
  }

  "RichPromise onFailure" should {
    "return the value when successful" in {
      val toopt: Promise[Int] = PurePromise(1).onFailure(2)
      val calc = await(toopt)
      calc mustEqual 1
    }

    "return the instead value a failure occurs" in {
      val rescue: Promise[Int] = PurePromise[Int](sys.error("try failed")).onFailure(1)
      val calc = await(rescue)
      calc mustEqual 1
    }
  }

}
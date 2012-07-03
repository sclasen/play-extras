package com.heroku.play.api.libs.concurrent

import org.slf4j.LoggerFactory
import play.api.libs.concurrent.{Thrown, Redeemed, Promise}


object RichPromise {

  val log = LoggerFactory.getLogger(classOf[RichPromise[_]])

  implicit def promise2RichPromise[T](promise: Promise[T]): RichPromise[T] = new RichPromise(promise)

}

class RichPromise[+T](promise: Promise[T]) {

  import RichPromise._

  def asOpt(errorToLog: String = "Exception thrown in toOpt promise, returning None"): Promise[Option[T]] = promise.extend {
    p => p.value match {
      case Redeemed(t) => Some(t)
      case Thrown(x) =>
        if (log.isDebugEnabled) {
          log.warn(errorToLog, x)
        } else {
          log.warn(errorToLog)
        }
        None
    }
  }

  def onFailure[S >: T](instead: S): Promise[S] = {
    val userPromise = Promise[S]()
    promise.extend {
      p => p.value match {
        case Redeemed(t) => userPromise.redeem(t)
        case Thrown(x) =>
          log.warn("tried promise failed,returning instead val", x)
          userPromise.redeem(instead)
      }
    }
    userPromise
  }

  def rescueWith[S >: T](rescuer: => Promise[S]): Promise[Option[S]] = {
    val userPromise = Promise[Option[S]]()
    promise.extend {
      p => p.value match {
        case Redeemed(t) => userPromise.redeem(Some(t))
        case Thrown(x) =>
          log.error("tried promise failed, attempting rescue", x)
          val rescueAttempt = rescuer
          rescueAttempt.extend {
            p => p.value match {
              case Redeemed(t) => userPromise.redeem(Some(t))
              case Thrown(x2) =>
                log.error("rescueAttempt failed", x2)
                userPromise.redeem(None)
            }
          }
      }
    }
    userPromise
  }

  def catching[S >: T](catcher: PartialFunction[Throwable, S]): Promise[S] = {
    val userPromise = Promise[S]()
    promise.extend {
      p => p.value match {
        case Redeemed(t) => userPromise.redeem(t)
        case Thrown(x) =>
          if (catcher.isDefinedAt(x)) userPromise.redeem(catcher.apply(x))
          else userPromise.throwing(x)
      }
    }
    userPromise
  }

}




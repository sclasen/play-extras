package com.heroku.play.api.libs.logging


import ch.qos.logback.core.{Layout, Appender}
import akka.actor.{ActorSystem, Props, Actor}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.spi.{FilterAttachableImpl, FilterReply, ContextAwareBase}
import java.util
import ch.qos.logback.core.filter.Filter
import reflect.BeanProperty
import com.typesafe.config.ConfigFactory

object ActorBasedConsoleAppender {
  lazy val conf = ConfigFactory.load("logging.conf")
  lazy val consoleActor = ActorSystem("logging", conf).actorOf(Props[ConsoleActor])
}

class ConsoleActor extends Actor {

  var layout: Layout[ILoggingEvent] = _

  protected def receive = {
    case s: String => print(s)
  }
}


class ActorBasedConsoleAppender extends ContextAwareBase with Appender[ILoggingEvent] {

  private val fai = new FilterAttachableImpl[ILoggingEvent]

  @BeanProperty
  var layout: Layout[ILoggingEvent] = _
  @BeanProperty
  var name: String = _

  @volatile
  var started: Boolean = false

  def doAppend(event: ILoggingEvent) {
    ActorBasedConsoleAppender.consoleActor ! layout.doLayout(event)
  }

  def start() {
    started = true
  }

  def stop() {
    started = false
  }

  def isStarted: Boolean = false

  def addFilter(newFilter: Filter[ILoggingEvent]) {
    fai.addFilter(newFilter)
  }

  def clearAllFilters() {
    fai.clearAllFilters()
  }

  def getCopyOfAttachedFiltersList: util.List[Filter[ILoggingEvent]] = fai.getCopyOfAttachedFiltersList

  def getFilterChainDecision(event: ILoggingEvent): FilterReply = fai.getFilterChainDecision(event)
}


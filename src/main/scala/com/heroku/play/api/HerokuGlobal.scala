package com.heroku.play.api

import org.slf4j.LoggerFactory
import play.api.GlobalSettings
import play.api.mvc.Results._
import play.api.mvc.{ Handler, Request, Result, RequestHeader }
import com.heroku.play.api.libs.json._
import com.heroku.play.api.libs.json.Formats._

trait HerokuGlobal extends GlobalSettings {
  val log = LoggerFactory.getLogger("Global")

  override def onError(request: RequestHeader, ex: Throwable) = {
    log.error("onError", ex)
    log.error("onError {}  {}", request, request.headers.toSimpleMap)
    InternalServerError(Error("an unexpected error occurred"))
  }

  override def onBadRequest(request: RequestHeader, error: String): Result = {
    log.error("onBadRequest {}", error)
    log.error("onBadRequest {}  {}", request, request.headers.toSimpleMap)
    if (request.isInstanceOf[Request[_]]) {
      log.error("onBadRequest body {}", request.asInstanceOf[Request[_]].body)
    }
    BadRequest(Error("Invalid Request Format"))
  }

  override def onHandlerNotFound(request: RequestHeader): Result = {
    log.error("onHandlerNotFound {}  {}", request, request.headers.toSimpleMap)
    NotFound(Error("Not Found"))
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    log.debug("routeRequest {}  {} ", request.method, request.uri)
    super.onRouteRequest(request)
  }

}

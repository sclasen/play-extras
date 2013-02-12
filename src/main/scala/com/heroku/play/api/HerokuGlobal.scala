package com.heroku.play.api

import mvc.Error
import org.slf4j.LoggerFactory
import play.api.GlobalSettings
import play.api.http.ContentTypes._
import play.api.http.HeaderNames._
import play.api.mvc.Results._
import play.api.mvc.{ Handler, Request, Result, RequestHeader }
import play.api.libs.json.Json._

trait HerokuGlobal extends GlobalSettings {
  val log = LoggerFactory.getLogger("Global")

  override def onError(request: RequestHeader, ex: Throwable) = {
    log.error("onError", ex)
    log.error("onError {}  {}", request, request.headers.toSimpleMap)
    InternalServerError(stringify(Error("an unexpected error occurred").json)).withHeaders(CONTENT_TYPE -> JSON)
  }

  override def onBadRequest(request: RequestHeader, error: String): Result = {
    log.error("onBadRequest {}", error)
    log.error("onBadRequest {}  {}", request, request.headers.toSimpleMap)
    if (request.isInstanceOf[Request[_]]) {
      log.error("onBadRequest body {}", request.asInstanceOf[Request[_]].body)
    }
    BadRequest(stringify(Error("Invalid Request Format").json)).withHeaders(CONTENT_TYPE -> JSON)
  }

  override def onHandlerNotFound(request: RequestHeader): Result = {
    log.error("onHandlerNotFound {}  {}", request, request.headers.toSimpleMap)
    NotFound(stringify(Error("Not Found").json)).withHeaders(CONTENT_TYPE -> JSON)
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    log.debug("routeRequest {}  {} ", request.method, request.uri)
    super.onRouteRequest(request)
  }

}

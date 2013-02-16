package com.heroku.play.api.libs.ws

import play.api.libs.iteratee.{ Concurrent, Enumerator }
import com.ning.http.client._
import com.ning.http.client.AsyncHandler.STATE
import play.api.mvc.{ Results, Result, ResponseHeader, SimpleResult }
import play.api.libs.ws.WS
import concurrent.{ Future, Promise, future, promise }
import play.api.libs.concurrent.Execution.Implicits.defaultContext

object WSProxy {

  def proxyGetAsync(url: String, responseHeadersToOverwrite: (String, String)*) = proxyRequestAsync(WS.client.prepareGet(url).build(), responseHeadersToOverwrite.toMap)

  def proxyGetAsyncAuthenticated(url: String, authHeaderValue: String, responseHeadersToOverwrite: (String, String)*) = proxyRequestAsync(WS.client.prepareGet(url).addHeader("AUTHORIZATION", authHeaderValue).build(), responseHeadersToOverwrite.toMap)

  def proxyRequestAsync(req: Request, responseHeadersToOverwrite: Map[String, String] = Map.empty): Future[Result] = {
    val (enum, channel) = Concurrent.broadcast[Array[Byte]]
    val headers = promise[HttpResponseHeaders]()
    val status = promise[Int]()

    WS.client.executeRequest(req, new AsyncHandler[Unit] {
      def onThrowable(p1: Throwable) {
        channel.end(p1)
      }

      def onBodyPartReceived(part: HttpResponseBodyPart): STATE = {
        channel.push(part.getBodyPartBytes)
        STATE.CONTINUE
      }

      def onStatusReceived(s: HttpResponseStatus): STATE = {
        status.success(s.getStatusCode)
        STATE.CONTINUE
      }

      def onHeadersReceived(h: HttpResponseHeaders): STATE = {
        headers.success(h)
        if (h.getHeaders.containsKey("Content-Length") && h.getHeaders.get("Content-Length").get(0) != "0") {
          STATE.CONTINUE
        } else {
          STATE.ABORT
        }
      }

      def onCompleted() {
        channel.end()
      }
    })

    import collection.JavaConverters._

    status.future.flatMap {
      s =>
        headers.future.map {
          h =>
            val hmap = h.getHeaders.iterator().asScala.map {
              entry => entry.getKey -> entry.getValue.get(0)
            }.toMap ++ responseHeadersToOverwrite
            if (h.getHeaders.containsKey("Content-Length") && h.getHeaders.get("Content-Length").get(0) != "0") {
              SimpleResult(ResponseHeader(s, hmap), enum)
            } else {
              SimpleResult(ResponseHeader(s, hmap), Enumerator(Results.EmptyContent()))
            }
        }
    }

  }

}

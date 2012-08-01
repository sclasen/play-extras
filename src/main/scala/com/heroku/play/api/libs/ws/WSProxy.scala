package com.heroku.play.api.libs.ws

import play.api.libs.iteratee.Enumerator
import play.api.libs.concurrent.Promise
import com.ning.http.client._
import com.ning.http.client.AsyncHandler.STATE
import play.api.mvc.Result
import play.api.mvc.ResponseHeader
import play.api.mvc.SimpleResult
import play.api.libs.ws.WS


object WSProxy {


  def proxyGetAsync(url: String, responseHeadersToOverwrite: (String, String)*) = proxyRequestAsync(WS.client.prepareGet(url).build(), responseHeadersToOverwrite.toMap)

  def proxyGetAsyncAuthenticated(url: String, authHeaderValue: String, responseHeadersToOverwrite: (String, String)*) = proxyRequestAsync(WS.client.prepareGet(url).addHeader("AUTHORIZATION", authHeaderValue).build(), responseHeadersToOverwrite.toMap)

  def proxyRequestAsync(req: Request, responseHeadersToOverwrite: Map[String, String] = Map.empty): Promise[Result] = {
    val enum = Enumerator.imperative[Array[Byte]]()
    val headers = Promise[HttpResponseHeaders]()
    val status = Promise[Int]()


    WS.client.executeRequest(req, new AsyncHandler[Unit] {
      def onThrowable(p1: Throwable) {
        enum.close()
      }

      def onBodyPartReceived(p1: HttpResponseBodyPart): STATE = {
        enum.push(p1.getBodyPartBytes)
        STATE.CONTINUE
      }

      def onStatusReceived(p1: HttpResponseStatus): STATE = {
        status.redeem(p1.getStatusCode)
        STATE.CONTINUE
      }

      def onHeadersReceived(p1: HttpResponseHeaders): STATE = {
        headers.redeem(p1)
        STATE.CONTINUE
      }

      def onCompleted() {
        enum.close()
      }
    })

    import collection.JavaConverters._

    status.flatMap {
      s => headers.map {
        h =>
          val hmap = h.getHeaders.iterator().asScala.map {
            entry => entry.getKey -> entry.getValue.get(0)
          }.toMap ++ responseHeadersToOverwrite
          SimpleResult(ResponseHeader(s, hmap), enum)
      }
    }

  }


}

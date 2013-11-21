package com.heroku.play.api.libs.ws

import play.api.libs.iteratee.{ Step, Iteratee, Concurrent, Enumerator }
import com.ning.http.client._
import com.ning.http.client.AsyncHandler.STATE
import play.api.mvc._
import play.api.libs.ws.WS
import concurrent.{ Future, Promise, future, promise }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.ResponseHeader
import com.ning.http.client.Request
import play.api.mvc.SimpleResult
import play.api.http.Status

object WSProxy extends Controller {

  def proxyGetAsync(url: String, responseHeadersToOverwrite: (String, String)*) = proxyRequestAsync(WS.client.prepareGet(url).build(), responseHeadersToOverwrite.toMap)

  def proxyGetAsyncAuthenticated(url: String, authHeaderValue: String, responseHeadersToOverwrite: (String, String)*) = proxyRequestAsync(WS.client.prepareGet(url).addHeader("AUTHORIZATION", authHeaderValue).build(), responseHeadersToOverwrite.toMap)

  def proxyRequestAsync(req: Request, responseHeadersToOverwrite: Map[String, String] = Map.empty): Future[Result] = {
    val enum = Enumerator.imperative[Array[Byte]]()
    val headers = promise[HttpResponseHeaders]()
    val status = promise[Int]()

    WS.client.executeRequest(req, new AsyncHandler[Unit] {
      def onThrowable(p1: Throwable) {
      }

      def onBodyPartReceived(part: HttpResponseBodyPart): STATE = {
        while (!enum.push(part.getBodyPartBytes)) {
          Thread.sleep(10)
        }
        STATE.CONTINUE
      }

      def onStatusReceived(s: HttpResponseStatus): STATE = {
        status.success(s.getStatusCode)
        STATE.CONTINUE
      }

      def onHeadersReceived(h: HttpResponseHeaders): STATE = {
        headers.success(h)
        if (h.getHeaders.containsKey("transfer-encoding") && h.getHeaders.get("transfer-encoding").get(0) == "chunked") {
          STATE.CONTINUE
        } else if (h.getHeaders.containsKey("Content-Length") && h.getHeaders.get("Content-Length").get(0) != "0") {
          STATE.CONTINUE
        } else {
          STATE.ABORT
        }
      }

      def onCompleted() {
        enum.close()
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
            if (h.getHeaders.containsKey("transfer-encoding") && h.getHeaders.get("transfer-encoding").get(0) == "chunked") {
              Status(s).stream(enum).withHeaders(hmap.toSeq: _*)
            } else if (h.getHeaders.containsKey("Content-Length") && h.getHeaders.get("Content-Length").get(0) != "0") {
              SimpleResult(ResponseHeader(s, hmap), enum)
            } else {
              SimpleResult(ResponseHeader(s, hmap), Enumerator(Results.EmptyContent()))
            }
        }
    }

  }

  def betterProxyRequestAsync(url: String, responseHeadersToOverwrite: Map[String, String] = Map.empty): Future[Result] = {
    val result = Promise[PlainResult]
    WS.url(url).get { responseHeader =>
      val (iteratee, enumerator) = joined[Array[Byte]]
      // depending on whether you have a content length, you may need to apply the Results.chunked enumeratee and add chunked headers to the result here.
      val headers = responseHeader.headers.map {
        case (k, v) => k -> v.head
      }
      result.trySuccess {
        responseHeader.headers.get("Content-Length").map {
          _ =>
            SimpleResult(ResponseHeader(responseHeader.status, headers), enumerator)
        }.getOrElse {
          Status(responseHeader.status).stream(enumerator).withHeaders(headers.toSeq: _*)
        }
      }
      iteratee
    }.recover {
      case _ =>
        result.trySuccess(InternalServerError)
        Iteratee.ignore
    }.flatMap(_.run) // <- very important, don't forget this one, otherwise
    result.future
  }

  def joined[A]: (Iteratee[A, Unit], Enumerator[A]) = {
    val promisedIteratee = Promise[Iteratee[A, Unit]]()
    val enumerator = new Enumerator[A] {
      def apply[B](i: Iteratee[A, B]) = {
        val doneIteratee = Promise[Iteratee[A, B]]()

        // Equivalent to map, but allows us to handle failures
        def wrap(delegate: Iteratee[A, B]): Iteratee[A, B] = new Iteratee[A, B] {
          def fold[C](folder: (Step[A, B]) => Future[C]) = {
            val toReturn = delegate.fold {
              case done @ Step.Done(a, in) => {
                doneIteratee.success(done.it)
                folder(done)
              }
              case Step.Cont(k) => {
                folder(Step.Cont(k.andThen(wrap)))
              }
              case err => folder(err)
            }
            toReturn.onFailure {
              case e => doneIteratee.failure(e)
            }
            toReturn
          }
        }

        if (promisedIteratee.trySuccess(wrap(i).map(_ => ()))) {
          doneIteratee.future
        } else {
          throw new IllegalStateException("Joined enumerator may only be applied once")
        }
      }
    }
    (Iteratee.flatten(promisedIteratee.future), enumerator)
  }

}

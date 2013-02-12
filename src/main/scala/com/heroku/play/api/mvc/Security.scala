package com.heroku.play.api.mvc

import play.api.Play.current
import play.api.mvc.Results._
import play.api.mvc._

trait Security {
  /*require SSL and redirect to https if not ssl*/
  def SSLRequired[A](p: BodyParser[A])(f: Request[A] => Result): Action[A] = Action(p) {
    request =>
      if (play.api.Play.isDev) f(request)
      else {
        if (request.headers.get("X-FORWARDED-PROTO").map(_ == "https").getOrElse(false)) f(request)
        else SeeOther("https://" + request.host + request.uri)
      }
  }

  def SSLRequired(f: Request[AnyContent] => Result): Action[AnyContent] = {
    SSLRequired(BodyParsers.parse.anyContent)(f)
  }

}

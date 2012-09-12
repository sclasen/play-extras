package com.heroku.play.api.libs.mailgun

import play.api.Play._
import com.heroku.play.api.libs.mvc
import play.api.libs.concurrent.{PurePromise, Promise}
import play.api.libs.ws.{Response, WS}
import play.api.http.HeaderNames._
import play.api.http.ContentTypes._
import MailgunService._
import com.codahale.jerkson.{Json => Jerkson}
import org.slf4j.LoggerFactory
import java.net.URLEncoder

object MailgunService {

  val log = LoggerFactory.getLogger("MailgunService")
  val mailgunBaseUrl = "https://api.mailgun.net/v2"

  def apply(): MailgunService = {
    val key = current.configuration.getString("mailgun.api.key").getOrElse(sys.error("mailgun.api.key not found in config"))
    val domain = current.configuration.getString("mailgun.api.domain").getOrElse(sys.error("mailgun.api.domain not found in config"))
    new MailgunService(key, domain)
  }

  def apply(apiKey: String, domain: String): MailgunService = {
    new MailgunService(apiKey, domain)
  }
}


class MailgunService(apiKey: String, val mailgunDomain: String) {

  val mailgunBasicAuth = mvc.BasicAuth("api", apiKey)


  def createMailingList(address: String, domain: Option[String] = None, name: Option[String] = None, description: Option[String] = None): Promise[MailgunResponse[ListResponse]] = {
    val email = domain.map(d => address + "@" + d).getOrElse(address + "@" + mailgunDomain)
    var fields = Map("address" -> email)
    post("/lists", fields).map(parseResp[ListResponse]).flatMap {
      case ErrorResponse(400, msg) if msg.endsWith("already exists") => getMailingList(address, domain)
      case x@_ => PurePromise(x)
    }
  }

  def getMailingLists(): Promise[MailgunResponse[MailingListList]] = {
    prepare("/lists").get().map(parseResp[MailingListList])
  }

  def getMailingList(address: String, domain: Option[String] = None): Promise[MailgunResponse[ListResponse]] = {
    val email = domain.map(d => address + "@" + d).getOrElse(address + "@" + mailgunDomain)
    prepare("/lists/" + email).get().map(parseResp[ListResponse])
  }

  def deleteMailingList(address: String, domain: Option[String] = None): Promise[MailgunResponse[Unit]] = {
    val email = domain.map(d => address + "@" + d).getOrElse(address + "@" + mailgunDomain)
    prepare("/lists/" + email).delete().map {
      resp =>
        if (resp.status == 200) OkResponse(())
        else err(resp)
    }
  }

  def addMemberToList(member: String, list: String, listDomain: Option[String] = None): Promise[MailgunResponse[MemberResponse]] = {
    val listMail = listDomain.map(d => list + "@" + d).getOrElse(list + "@" + mailgunDomain)
    var fields = Map("address" -> member, "upsert" -> "yes")
    post("/lists/" + listMail + "/members", fields).map(parseResp[MemberResponse])
  }

  def listMembers(list: String, listDomain: Option[String] = None): Promise[MailgunResponse[MemberList]] = {
    val listMail = listDomain.map(d => list + "@" + d).getOrElse(list + "@" + mailgunDomain)
    prepare("/lists/" + listMail + "/members").get().map(parseResp[MemberList])
  }


  def removeMemberFromList(member: String, list: String, listDomain: Option[String] = None): Promise[MailgunResponse[MemberResponse]] = {
    val listMail = listDomain.map(d => list + "@" + d).getOrElse(list + "@" + mailgunDomain)
    prepare("/lists/" + listMail + "/members/" + member).delete().map(parseResp[MemberResponse])
  }

  private def post(path: String, fields: Map[String, String]): Promise[Response] = prepare(path).withHeaders(CONTENT_TYPE -> FORM).post(encode(fields))

  private def encode(fields: Map[String, String]) = fields.map {
    case (k, v) => k + "=" + URLEncoder.encode(v, "UTF-8")
  }.reduceLeft(_ + "&" + _)

  private def prepare(path: String) = {
    WS.url(mailgunBaseUrl + path).withHeaders(AUTHORIZATION -> mailgunBasicAuth, ACCEPT -> "application/json")
  }

  private def parseResp[T: Manifest](resp: Response): MailgunResponse[T] = {
    if (resp.status == 200) {
      ok[T](resp)
    } else {
      err(resp)
    }
  }

  private def parse[T: Manifest](resp: Response, fn: T => MailgunResponse[T]): MailgunResponse[T] = {
    try {
      fn(Jerkson.parse[T](resp.body))
    } catch {
      case e: Exception => ErrorResponse(resp.status, "Unable to parse response:" + resp.body)
    }
  }

  private def ok[T: Manifest](resp: Response) = parse[T](resp, OkResponse(_))

  private def err(resp: Response) = {
    val atts = Jerkson.parse[Map[String, String]](resp.body)
    ErrorResponse(resp.status, atts.get("message").getOrElse("no message"))
  }

}

sealed trait MailgunResponse[+T]

case class OkResponse[T](ok: T) extends MailgunResponse[T]

case class ErrorResponse(status: Int, message: String) extends MailgunResponse[Nothing]

case class MailingList(members_count: Int, description: String, created_at: String, access_level: String, address: String, name: String)

case class ListResponse(message: Option[String], list: MailingList)

case class MemberResponse(message: Option[String], member: Member)

case class Member(address: String)

case class MemberList(items: List[Member])

case class MailingListList(items: List[MailingList])
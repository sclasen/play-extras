package com.heroku.play.api.libs.mailgun

import play.api.Play._
import com.heroku.play.api.libs.mvc
import play.api.libs.ws.{ Response, WS }
import play.api.http.HeaderNames._
import play.api.http.ContentTypes._
import MailgunService._
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import concurrent.Future
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

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

  implicit val m = Json.reads[Member]
  implicit val me = (__ \ "items").read[List[Member]].map { l => MemberList(l) }
  implicit val ro = Json.reads[Route]
  implicit val cr = Json.reads[CreateRoute]
  implicit val rr = Json.reads[RouteResponse]
  implicit val mr = Json.reads[MemberResponse]
  implicit val ml = Json.reads[MailingList]
  implicit val lr = Json.reads[ListResponse]
  implicit val mll = (__ \ "items").read[List[MailingList]].map { l => MailingListList(l) }
  implicit val rd = Json.reads[RouteUpdated]
  implicit val ru = Json.reads[RouteDeleted]
  implicit val rl = Json.reads[RoutesList]
  implicit val err = Json.reads[ErrorResponse]
  implicit val mb = Json.reads[Mailbox]
  implicit val mbl = Json.reads[MailboxList]
  implicit val mer = Json.reads[MessageResponse]
  implicit val md = Json.reads[MailboxDeleted]
  implicit def ok[T](implicit r: Reads[T]) = (__ \ "ok").read[T].map(t => OkResponse(t))
  implicit val mmm: Reads[MailgunMimeMessage] = (
    (__ \ "To").read[String] ~
    (__ \ "sender").read[String] ~
    (__ \ "from").read[String] ~
    (__ \ "subject").read[String] ~
    (__ \ "body-mime").read[String])(MailgunMimeMessage.apply _)
  implicit val mmd = Json.reads[MaligunMimeDeleted]
}

class MailgunService(apiKey: String, val mailgunDomain: String) {

  val mailgunBasicAuth = mvc.BasicAuth("api", apiKey)

  def createMailingList(address: String, domain: Option[String] = None, name: Option[String] = None, description: Option[String] = None): Future[MailgunResponse[ListResponse]] = {
    val email = domain.map(d => address + "@" + d).getOrElse(address + "@" + mailgunDomain)
    post("/lists", "address" -> email).map(parseResp[ListResponse]).flatMap {
      case ErrorResponse(400, msg) if msg.endsWith("already exists") => getMailingList(address, domain)
      case x @ _ => Future(x)
    }
  }

  def getMailingLists(limit: Int = 100, skip: Int = 0): Future[MailgunResponse[MailingListList]] = {
    prepare("/lists").withQueryString("limit" -> limit.toString, "skip" -> skip.toString).get().map(parseResp[MailingListList])
  }

  private def getMailingLists(list: MailingListList, skip: Int): Future[MailgunResponse[MailingListList]] = {
    getMailingLists(100, skip).flatMap {
      case o @ OkResponse(m @ MailingListList(items)) if items.size < 100 => Future(OkResponse(list + m))
      case OkResponse(m @ MailingListList(_)) => getMailingLists(list + m, skip + 100)
      case e: ErrorResponse => Future(e)
    }
  }

  def getAllMailingLists(): Future[MailgunResponse[MailingListList]] = {
    getMailingLists(MailingListList(List.empty), 0)
  }

  def getMailingList(address: String, domain: Option[String] = None): Future[MailgunResponse[ListResponse]] = {
    val email = domain.map(d => address + "@" + d).getOrElse(address + "@" + mailgunDomain)
    prepare("/lists/" + email).get().map(parseResp[ListResponse])
  }

  def deleteMailingList(address: String, domain: Option[String] = None): Future[MailgunResponse[Unit]] = {
    val email = domain.map(d => address + "@" + d).getOrElse(address + "@" + mailgunDomain)
    prepare("/lists/" + email).delete().map {
      resp =>
        if (resp.status == 200) OkResponse(())
        else err(resp)
    }
  }

  def addMemberToList(member: String, list: String, listDomain: Option[String] = None): Future[MailgunResponse[MemberResponse]] = {
    val listMail = listDomain.map(d => list + "@" + d).getOrElse(list + "@" + mailgunDomain)
    post("/lists/" + listMail + "/members", "address" -> member, "upsert" -> "yes").map(parseResp[MemberResponse])
  }

  def listMembers(list: String, listDomain: Option[String] = None): Future[MailgunResponse[MemberList]] = {
    val listMail = listDomain.map(d => list + "@" + d).getOrElse(list + "@" + mailgunDomain)
    prepare("/lists/" + listMail + "/members").get().map(parseResp[MemberList])
  }

  def removeMemberFromList(member: String, list: String, listDomain: Option[String] = None): Future[MailgunResponse[MemberResponse]] = {
    val listMail = listDomain.map(d => list + "@" + d).getOrElse(list + "@" + mailgunDomain)
    prepare("/lists/" + listMail + "/members/" + member).delete().map(parseResp[MemberResponse])
  }

  def getRoutes(limit: Int = 100, skip: Int = 0): Future[MailgunResponse[RoutesList]] = {
    prepare("/routes").withQueryString("limit" -> limit.toString, "skip" -> skip.toString).get().map(parseResp[RoutesList])
  }

  private def getRoutes(list: RoutesList, skip: Int): Future[MailgunResponse[RoutesList]] = {
    getRoutes(100, skip).flatMap {
      case o @ OkResponse(r @ RoutesList(count, items)) if items.size < 100 => Future(OkResponse(list + r))
      case OkResponse(r @ RoutesList(_, _)) => getRoutes(list + r, skip + 100)
      case e: ErrorResponse => Future(e)
    }
  }

  def getAllRoutes(): Future[MailgunResponse[RoutesList]] = {
    getRoutes(RoutesList(0, List.empty), 0)
  }

  def getRoute(id: String): Future[MailgunResponse[RouteResponse]] = {
    prepare("/routes/" + id).get().map(parseResp[RouteResponse])
  }

  def updateRoute(route: Route): Future[MailgunResponse[RouteUpdated]] = {
    put("/routes/" + route.id, routesParams(route): _*).map(parseResp[RouteUpdated])
  }

  def createRoute(route: CreateRoute): Future[MailgunResponse[RouteResponse]] = {
    post("/routes", routesParams(route): _*).map(parseResp[RouteResponse])
  }

  def deleteRoute(id: String): Future[MailgunResponse[RouteDeleted]] = {
    prepare("/routes/" + id).delete().map(parseResp[RouteDeleted])
  }

  def createMailbox(name: String, password: String, domain: Option[String] = None): Future[MailgunResponse[MessageResponse]] = {
    val mboxDomain = domain.getOrElse(mailgunDomain)
    val mailbox = name + "@" + mboxDomain
    post("/" + mboxDomain + "/mailboxes", "mailbox" -> mailbox, "password" -> password).map(parseResp[MessageResponse])
  }

  def getMailboxes(limit: Int = 100, skip: Int = 0, domain: Option[String] = None): Future[MailgunResponse[MailboxList]] = {
    val mboxDomain = domain.getOrElse(mailgunDomain)
    prepare("/" + mboxDomain + "/mailboxes").withQueryString("limit" -> limit.toString, "skip" -> skip.toString).get().map(parseResp[MailboxList])
  }

  private def getMailboxes(list: MailboxList, skip: Int, domain: Option[String]): Future[MailgunResponse[MailboxList]] = {
    getMailboxes(100, skip, domain).flatMap {
      case o @ OkResponse(m @ MailboxList(count, items)) if items.size < 100 => Future(OkResponse(list + m))
      case OkResponse(m @ MailboxList(_, _)) => getMailboxes(list + m, skip + 100, domain)
      case e: ErrorResponse => Future(e)
    }
  }

  def getAllMailboxes(domain: Option[String] = None): Future[MailgunResponse[MailboxList]] = {
    getMailboxes(MailboxList(0, List.empty), 0, domain)
  }

  def updateMailboxPassword(name: String, password: String, domain: Option[String] = None): Future[MailgunResponse[MessageResponse]] = {
    val mboxDomain = domain.getOrElse(mailgunDomain)
    val mailbox = name + "@" + mboxDomain
    put("/" + mboxDomain + "/mailboxes/" + mailbox, "password" -> password).map {
      resp =>
        parseResp[MessageResponse](resp)
    }
  }

  def deleteMailbox(name: String, domain: Option[String] = None): Future[MailgunResponse[MailboxDeleted]] = {
    val mboxDomain = domain.getOrElse(mailgunDomain)
    val mailbox = name + "@" + mboxDomain
    prepare("/" + mboxDomain + "/mailboxes/" + mailbox).delete().map {
      resp =>
        parseResp[MailboxDeleted](resp)
    }
  }

  def getMessage(messageUrl: String): Future[MailgunResponse[MailgunMimeMessage]] = {
    messagePrepare(messageUrl).get().map(parseResp[MailgunMimeMessage])
  }

  def deleteMessage(messageUrl: String): Future[MailgunResponse[MaligunMimeDeleted]] = {
    messagePrepare(messageUrl).get().map(parseResp[MaligunMimeDeleted])
  }

  private def routesParams(route: Route) = Seq("priority" -> route.priority.toString, "description" -> route.description, "expression" -> route.expression) ++
    route.actions.map(a => "action" -> a)

  private def routesParams(route: CreateRoute) = Seq("priority" -> route.priority.toString, "description" -> route.description, "expression" -> route.expression) ++
    route.actions.map(a => "action" -> a)

  private def post(path: String, fields: (String, String)*): Future[Response] = prepare(path).withHeaders(CONTENT_TYPE -> FORM).post(encode(fields))

  private def put(path: String, fields: (String, String)*): Future[Response] = prepare(path).withHeaders(CONTENT_TYPE -> FORM).put(encode(fields))

  private def encode(fields: Seq[(String, String)]) = fields.map {
    case (k, v) => k + "=" + URLEncoder.encode(v, "UTF-8")
  }.reduceLeft(_ + "&" + _)

  private def prepare(path: String) = {
    WS.url(mailgunBaseUrl + path).withHeaders(AUTHORIZATION -> mailgunBasicAuth, ACCEPT -> "application/json")
  }

  private def messagePrepare(messageUrl: String) = {
    WS.url(messageUrl).withHeaders(AUTHORIZATION -> mailgunBasicAuth, ACCEPT -> "message/rfc2822")
  }

  private def parseResp[T](resp: Response)(implicit r: Reads[T]): MailgunResponse[T] = {
    if (resp.status == 200) {
      ok[T](resp)
    } else {
      err(resp)
    }
  }

  private def parse[T](resp: Response, fn: T => MailgunResponse[T])(implicit read: Reads[T]): MailgunResponse[T] = {
    try {
      fn(Json.parse(resp.body).as[T])
    } catch {
      case e: Exception => ErrorResponse(resp.status, "Unable to parse response:" + resp.body)
    }
  }

  private def ok[T](resp: Response)(implicit read: Reads[T]) = parse[T](resp, OkResponse(_))

  private def err(resp: Response) = {
    val atts = Json.parse(resp.body)
    ErrorResponse(resp.status, (atts \ "message").asOpt[String].getOrElse("no message"))
  }

}

sealed trait MailgunResponse[+T]

case class OkResponse[T](ok: T) extends MailgunResponse[T]

case class ErrorResponse(status: Int, message: String) extends MailgunResponse[Nothing]

case class MailingList(members_count: Int, description: String, created_at: String, access_level: String, address: String, name: String)

case class ListResponse(message: Option[String], list: MailingList)

case class MemberResponse(message: Option[String], member: Member)

case class RouteResponse(message: Option[String], route: Route)

case class CreateRoute(expression: String, actions: List[String], priority: Int = 0, description: String = "")

case class Route(expression: String, actions: List[String], priority: Int = 0, description: String = "", id: String, created_at: String)

case class Member(address: String)

case class MemberList(items: List[Member])

case class MailingListList(items: List[MailingList]) {
  def +(other: MailingListList) = this.copy(items = this.items ++ other.items)
}

case class RoutesList(total_count: Int, items: List[Route]) {
  def +(other: RoutesList): RoutesList = this.copy(total_count = this.total_count + other.total_count, items = this.items ++ other.items)
}

case class RouteDeleted(id: String, message: String)

case class RouteUpdated(expression: String, actions: List[String], priority: Int = 0, description: String = "", id: String, created_at: String, message: String)

case class Mailbox(mailbox: String, size_bytes: Option[Int], created_at: String)

case class MailboxList(total_count: Int, items: List[Mailbox]) {
  def +(other: MailboxList): MailboxList = this.copy(total_count = this.total_count + other.total_count, items = this.items ++ other.items)
}

case class MessageResponse(message: String)

case class MailboxDeleted(message: String, spec: String)

case class MailgunMimeMessage(To: String, sender: String, from: String, subject: String, body_mime: String)

case class MaligunMimeDeleted(message: String)


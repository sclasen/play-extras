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
    post("/lists", "address" -> email).map(parseResp[ListResponse]).flatMap {
      case ErrorResponse(400, msg) if msg.endsWith("already exists") => getMailingList(address, domain)
      case x@_ => PurePromise(x)
    }
  }

  def getMailingLists(limit: Int = 100, skip: Int = 0): Promise[MailgunResponse[MailingListList]] = {
    prepare("/lists").withQueryString("limit" -> limit.toString, "skip" -> skip.toString).get().map(parseResp[MailingListList])
  }


  private def getMailingLists(list: MailingListList, skip: Int): Promise[MailgunResponse[MailingListList]] = {
    getMailingLists(100, skip).flatMap {
      case o@OkResponse(m@MailingListList(items)) if items.size < 100 => PurePromise(OkResponse(list + m))
      case OkResponse(m@MailingListList(_)) => getMailingLists(list + m, skip + 100)
      case e: ErrorResponse => PurePromise(e)
    }
  }

  def getAllMailingLists(): Promise[MailgunResponse[MailingListList]] = {
    getMailingLists(MailingListList(List.empty), 0)
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
    post("/lists/" + listMail + "/members", "address" -> member, "upsert" -> "yes").map(parseResp[MemberResponse])
  }

  def listMembers(list: String, listDomain: Option[String] = None): Promise[MailgunResponse[MemberList]] = {
    val listMail = listDomain.map(d => list + "@" + d).getOrElse(list + "@" + mailgunDomain)
    prepare("/lists/" + listMail + "/members").get().map(parseResp[MemberList])
  }


  def removeMemberFromList(member: String, list: String, listDomain: Option[String] = None): Promise[MailgunResponse[MemberResponse]] = {
    val listMail = listDomain.map(d => list + "@" + d).getOrElse(list + "@" + mailgunDomain)
    prepare("/lists/" + listMail + "/members/" + member).delete().map(parseResp[MemberResponse])
  }

  def getRoutes(limit: Int = 100, skip: Int = 0): Promise[MailgunResponse[RoutesList]] = {
    prepare("/routes").withQueryString("limit" -> limit.toString, "skip" -> skip.toString).get().map(parseResp[RoutesList])
  }

  private def getRoutes(list: RoutesList, skip: Int): Promise[MailgunResponse[RoutesList]] = {
    getRoutes(100, skip).flatMap {
      case o@OkResponse(r@RoutesList(count, items)) if items.size < 100 => PurePromise(OkResponse(list + r))
      case OkResponse(r@RoutesList(_, _)) => getRoutes(list + r, skip + 100)
      case e: ErrorResponse => PurePromise(e)
    }
  }

  def getAllRoutes(): Promise[MailgunResponse[RoutesList]] = {
    getRoutes(RoutesList(0, List.empty), 0)
  }

  def getRoute(id: String): Promise[MailgunResponse[RouteResponse]] = {
    prepare("/routes/" + id).get().map(parseResp[RouteResponse])
  }

  def updateRoute(route: Route): Promise[MailgunResponse[RouteUpdated]] = {
    put("/routes/" + route.id, routesParams(route): _*).map(parseResp[RouteUpdated])
  }

  def createRoute(route: CreateRoute): Promise[MailgunResponse[RouteResponse]] = {
    post("/routes", routesParams(route): _*).map(parseResp[RouteResponse])
  }

  def deleteRoute(id: String): Promise[MailgunResponse[RouteDeleted]] = {
    prepare("/routes/" + id).delete().map(parseResp[RouteDeleted])
  }

  def createMailbox(name: String, password: String, domain: Option[String] = None): Promise[MailgunResponse[MessageResponse]] = {
    val mboxDomain = domain.getOrElse(mailgunDomain)
    val mailbox = name + "@" + mboxDomain
    post("/" + mboxDomain + "/mailboxes", "mailbox" -> mailbox, "password" -> password).map(parseResp[MessageResponse])
  }

  def getMailboxes(limit: Int = 100, skip: Int = 0, domain: Option[String] = None): Promise[MailgunResponse[MailboxList]] = {
    val mboxDomain = domain.getOrElse(mailgunDomain)
    prepare("/" + mboxDomain + "/mailboxes").withQueryString("limit" -> limit.toString, "skip" -> skip.toString).get().map(parseResp[MailboxList])
  }

  private def getMailboxes(list: MailboxList, skip: Int, domain: Option[String]): Promise[MailgunResponse[MailboxList]] = {
    getMailboxes(100, skip, domain).flatMap {
      case o@OkResponse(m@MailboxList(count, items)) if items.size < 100 => PurePromise(OkResponse(list + m))
      case OkResponse(m@MailboxList(_, _)) => getMailboxes(list + m, skip + 100, domain)
      case e: ErrorResponse => PurePromise(e)
    }
  }

  def getAllMailboxes(domain: Option[String] = None): Promise[MailgunResponse[MailboxList]] = {
    getMailboxes(MailboxList(0, List.empty), 0, domain)
  }


  def updateMailboxPassword(name: String, password: String, domain: Option[String] = None): Promise[MailgunResponse[MessageResponse]] = {
    val mboxDomain = domain.getOrElse(mailgunDomain)
    val mailbox = name + "@" + mboxDomain
    put("/" + mboxDomain + "/mailboxes/" + mailbox, "password" -> password).map {
      resp =>
        parseResp[MessageResponse](resp)
    }
  }

  def deleteMailbox(name: String, domain: Option[String] = None): Promise[MailgunResponse[MailboxDeleted]] = {
    val mboxDomain = domain.getOrElse(mailgunDomain)
    val mailbox = name + "@" + mboxDomain
    prepare("/" + mboxDomain + "/mailboxes/" + mailbox).delete().map {
      resp =>
        parseResp[MailboxDeleted](resp)
    }
  }


  private def routesParams(route: Route) = Seq("priority" -> route.priority.toString, "description" -> route.description, "expression" -> route.expression) ++
    route.actions.map(a => "action" -> a)

  private def routesParams(route: CreateRoute) = Seq("priority" -> route.priority.toString, "description" -> route.description, "expression" -> route.expression) ++
    route.actions.map(a => "action" -> a)

  private def post(path: String, fields: (String, String)*): Promise[Response] = prepare(path).withHeaders(CONTENT_TYPE -> FORM).post(encode(fields))

  private def put(path: String, fields: (String, String)*): Promise[Response] = prepare(path).withHeaders(CONTENT_TYPE -> FORM).put(encode(fields))

  private def encode(fields: Seq[(String, String)]) = fields.map {
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




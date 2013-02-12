package com.heroku.play.api.libs.mailgun

import org.specs2.mutable.Specification
import java.util.concurrent.TimeUnit
import play.api.test.Helpers._
import play.api.test.FakeApplication
import org.specs2.matcher.MatchResult
import play.api.libs.concurrent.Promise
import parallel.Future
import concurrent.duration.Duration
import concurrent.Await

class MailgunServiceSpec extends Specification {

  val domain = sys.env("MAILGUN_API_DOMAIN")
  val config = Map("mailgun.api.key" -> sys.env("MAILGUN_API_KEY"), "mailgun.api.domain" -> sys.env("MAILGUN_API_DOMAIN"))
  val list = "test" + System.currentTimeMillis()

  val listEmail = list + "@" + config("mailgun.api.domain")
  val userEmail = "user+" + listEmail

  val mailbox = "mailbox"
  val mailboxAddresss = mailbox + "@" + config("mailgun.api.domain")

  var route: Route = _

  "MailgunService" should {
    sequential

    "create a mailing list " in {
      withMailgun {
        svc =>
          var resp = Await.result(svc.createMailingList(list, None, Some(list), Some(list)), Duration(5, TimeUnit.SECONDS))
          resp match {
            case OkResponse((ListResponse(_, MailingList(_, _, _,
              _, email, _)))) => email mustEqual (listEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
          resp = Await.result(svc.createMailingList(list, None, Some(list), Some(list)), Duration(5, TimeUnit.SECONDS))
          resp match {
            case OkResponse((ListResponse(_, MailingList(_, _, _, _, email, _)))) => email mustEqual (listEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "get all lists" in {
      withMailgun {
        svc =>
          Await.result(Promise.sequence {
            (0 to 100).map {
              i => svc.createMailingList("alltest" + i)
            }
          }, Duration(5, TimeUnit.SECONDS))
          val resp = Await.result(svc.getAllMailingLists(), Duration(5, TimeUnit.SECONDS))
          resp match {
            case OkResponse(lists) =>
              lists.items.find(_.address == listEmail).isDefined mustEqual true
              lists.items.size > 100 mustEqual (true)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "get a single list" in {
      withMailgun {
        svc =>
          val resp = Await.result(svc.getMailingList(list), Duration(5, TimeUnit.SECONDS))
          resp match {
            case OkResponse((ListResponse(_, MailingList(_, _, _, _, email, _)))) => email mustEqual (listEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "add a member to a list " in {
      withMailgun {
        svc =>
          val resp = Await.result(svc.addMemberToList(userEmail, list), Duration(5, TimeUnit.SECONDS))
          resp match {
            case OkResponse(MemberResponse(_, Member(email))) => email mustEqual (userEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "list the members of a list" in {
      withMailgun {
        svc =>
          val resp = Await.result(svc.listMembers(list), Duration(5, TimeUnit.SECONDS))
          resp match {
            case OkResponse(MemberList(members)) => members(0).address mustEqual (userEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "remove a member from a list " in {
      withMailgun {
        svc =>
          val resp = Await.result(svc.removeMemberFromList(userEmail, list), Duration(5, TimeUnit.SECONDS))
          resp match {
            case OkResponse(MemberResponse(_, Member(email))) => email mustEqual (userEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "delete a list" in {
      withMailgun {
        svc =>
          val resp = Await.result(svc.deleteMailingList(listEmail), Duration(5, TimeUnit.SECONDS))
          resp match {
            case OkResponse(_) => true mustEqual (true)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "create a route" in withMailgun {
      svc =>
        Await.result(svc.createRoute(CreateRoute("""match_recipient(".*@%s")""".format(domain), List("stop()"))), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(RouteResponse(_, r)) =>
            route = r
            true mustEqual (true)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "list routes" in withMailgun {
      svc =>
        Await.result(svc.getAllRoutes(), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(rlist) => rlist.items.find(_.id == route.id).isDefined mustEqual true
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "get a route" in withMailgun {
      svc =>
        Await.result(svc.getRoute(route.id), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(RouteResponse(_, r)) => r.id mustEqual (route.id)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "update a route" in withMailgun {
      svc =>
        Await.result(svc.updateRoute(route.copy(priority = 2)), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(RouteUpdated(_, _, p, _, i, _, _)) =>
            i mustEqual (route.id)
            p mustEqual (2)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "delete a route" in withMailgun {
      svc =>
        Await.result(svc.deleteRoute(route.id), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(RouteDeleted(i, m)) => i mustEqual (route.id)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "create a mailbox" in withMailgun {
      svc =>
        Await.result(svc.createMailbox(mailbox, "asdfghjl"), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(MessageResponse(msg)) => msg != null mustEqual (true)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "list  mailboxes" in withMailgun {
      svc =>
        Await.result(svc.getAllMailboxes(), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(MailboxList(count, mlist)) => mlist.find(_.mailbox == mailboxAddresss).isDefined mustEqual true
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "update  mailbox password" in withMailgun {
      svc =>
        Await.result(svc.updateMailboxPassword(mailbox, "qwert"), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(MessageResponse(msg)) => msg != null mustEqual (true)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }
    "delete a  mailbox" in withMailgun {
      svc =>
        Await.result(svc.deleteMailbox(mailbox), Duration(5, TimeUnit.SECONDS)) match {
          case OkResponse(MailboxDeleted(msg, spec)) => msg != null mustEqual (true)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

  }

  def withMailgun(block: MailgunService => MatchResult[Any]): MatchResult[Any] = running(FakeApplication(additionalConfiguration = config)) {
    val svc = MailgunService()
    block(svc)
  }

}

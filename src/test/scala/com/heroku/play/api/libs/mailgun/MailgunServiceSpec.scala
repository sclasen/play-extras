package com.heroku.play.api.libs.mailgun

import org.specs2.mutable.Specification
import java.util.concurrent.TimeUnit
import play.api.test.Helpers._
import play.api.test.FakeApplication
import org.specs2.matcher.MatchResult
import play.api.libs.concurrent.Promise


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
          var resp = svc.createMailingList(list, None, Some(list), Some(list)).await(5, TimeUnit.SECONDS).get
          resp match {
            case OkResponse((ListResponse(_, MailingList(_, _, _, _, email, _)))) => email mustEqual (listEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
          resp = svc.createMailingList(list, None, Some(list), Some(list)).await(5, TimeUnit.SECONDS).get
          resp match {
            case OkResponse((ListResponse(_, MailingList(_, _, _, _, email, _)))) => email mustEqual (listEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }


    "get all lists" in {
      withMailgun {
        svc =>
          Promise.sequence {
            (0 to 100).map {
              i => svc.createMailingList("alltest" + i)
            }
          }.await(30, TimeUnit.SECONDS)
          val resp = svc.getAllMailingLists().await(5, TimeUnit.SECONDS).get
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
          val resp = svc.getMailingList(list).await(5, TimeUnit.SECONDS).get
          resp match {
            case OkResponse((ListResponse(_, MailingList(_, _, _, _, email, _)))) => email mustEqual (listEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "add a member to a list " in {
      withMailgun {
        svc =>
          val resp = svc.addMemberToList(userEmail, list).await(5, TimeUnit.SECONDS).get
          resp match {
            case OkResponse(MemberResponse(_, Member(email))) => email mustEqual (userEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "list the members of a list" in {
      withMailgun {
        svc =>
          val resp = svc.listMembers(list).await(5, TimeUnit.SECONDS).get
          resp match {
            case OkResponse(MemberList(members)) => members(0).address mustEqual (userEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }


    "remove a member from a list " in {
      withMailgun {
        svc =>
          val resp = svc.removeMemberFromList(userEmail, list).await(5, TimeUnit.SECONDS).get
          resp match {
            case OkResponse(MemberResponse(_, Member(email))) => email mustEqual (userEmail)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }




    "delete a list" in {
      withMailgun {
        svc =>
          val resp = svc.deleteMailingList(listEmail).await(5, TimeUnit.SECONDS).get
          resp match {
            case OkResponse(_) => true mustEqual (true)
            case ErrorResponse(_, msg) => failure(msg)
          }
      }
    }

    "create a route" in withMailgun {
      svc =>
        svc.createRoute(CreateRoute( """match_recipient(".*@%s")""".format(domain), List("stop()"))).await(5, TimeUnit.SECONDS).get match {
          case OkResponse(RouteResponse(_, r)) =>
            route = r
            true mustEqual (true)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "list routes" in withMailgun {
      svc =>
        svc.getAllRoutes().await(5, TimeUnit.SECONDS).get match {
          case OkResponse(rlist) => rlist.items.find(_.id == route.id).isDefined mustEqual true
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "get a route" in withMailgun {
      svc =>
        svc.getRoute(route.id).await(5, TimeUnit.SECONDS).get match {
          case OkResponse(RouteResponse(_, r)) => r.id mustEqual (route.id)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "update a route" in withMailgun {
      svc =>
        svc.updateRoute(route.copy(priority = 2)).await(5, TimeUnit.SECONDS).get match {
          case OkResponse(RouteUpdated(_, _, p, _, i, _, _)) =>
            i mustEqual (route.id)
            p mustEqual (2)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "delete a route" in withMailgun {
      svc =>
        svc.deleteRoute(route.id).await(5, TimeUnit.SECONDS).get match {
          case OkResponse(RouteDeleted(i, m)) => i mustEqual (route.id)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "create a mailbox" in withMailgun {
      svc =>
        svc.createMailbox(mailbox, "asdfghjl").await(5, TimeUnit.SECONDS).get match {
          case OkResponse(MessageResponse(msg)) => msg != null mustEqual (true)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "list  mailboxes" in withMailgun {
      svc =>
        svc.getAllMailboxes().await(5, TimeUnit.SECONDS).get match {
          case OkResponse(MailboxList(count, mlist)) => mlist.find(_.mailbox == mailboxAddresss).isDefined mustEqual true
          case ErrorResponse(_, msg) => failure(msg)
        }
    }

    "update  mailbox password" in withMailgun {
      svc =>
        svc.updateMailboxPassword(mailbox, "qwert").await(5, TimeUnit.SECONDS).get match {
          case OkResponse(MessageResponse(msg)) => msg != null mustEqual (true)
          case ErrorResponse(_, msg) => failure(msg)
        }


    }
    "delete a  mailbox" in withMailgun {
      svc =>
        svc.deleteMailbox(mailbox).await(5, TimeUnit.SECONDS).get match {
          case OkResponse(MailboxDeleted(msg, spec)) => msg != null mustEqual (true)
          case ErrorResponse(_, msg) => failure(msg)
        }
    }


  }

  def withMailgun(block: MailgunService => MatchResult[Any]): MatchResult[Any] = running(FakeApplication(additionalConfiguration = config, withoutPlugins = Seq("play.api.cache.EhCachePlugin"))) {
    val svc = MailgunService()
    block(svc)
  }

}

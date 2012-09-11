package com.heroku.play.api.libs.mailgun

import org.specs2.mutable.Specification
import java.util.concurrent.TimeUnit
import play.api.test.Helpers._
import play.api.test.FakeApplication


class MailgunServiceSpec extends Specification {

  val config = Map("mailgun.api.key" -> sys.env("MAILGUN_API_KEY"), "mailgun.api.domain" -> sys.env("MAILGUN_API_DOMAIN"))
  val list = "test" + System.currentTimeMillis()

  val listEmail = list + "@" + config("mailgun.api.domain")
  val userEmail = "user" + listEmail

  "MailgunService" should {
    sequential

    "create a mailing list " in {
      running(FakeApplication(additionalConfiguration = config)) {
        val svc = MailgunService()
        val resp = svc.createMailingList(list, None, Some(list), Some(list)).await(5, TimeUnit.SECONDS).get
        resp match {
          case OkResponse((ListResponse(_, MailingList(_, _, _, _, email, _)))) => email mustEqual (listEmail)
          case ErrorResponse(MailgunError(_, msg)) => failure(msg)
        }
      }
    }


    "get all lists" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val svc = MailgunService()
        val resp = svc.getMailingLists().await(5, TimeUnit.SECONDS).get
        resp match {
          case OkResponse(lists) => lists.items.find(_.address == listEmail).isDefined mustEqual true
          case ErrorResponse(MailgunError(_, msg)) => failure(msg)
        }
      }
    }

    "get a single list" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val svc = MailgunService()
        val resp = svc.getMailingList(list).await(5, TimeUnit.SECONDS).get
        resp match {
          case OkResponse((ListResponse(_, MailingList(_, _, _, _, email, _)))) => email mustEqual (listEmail)
          case ErrorResponse(MailgunError(_, msg)) => failure(msg)
        }
      }
    }

    "add a member to a list " in {
      running(FakeApplication(additionalConfiguration = config)) {
        val svc = MailgunService()
        val resp = svc.addMemberToList(userEmail, list).await(5, TimeUnit.SECONDS).get
        resp match {
          case OkResponse(MemberResponse(_, Member(email))) => email mustEqual (userEmail)
          case ErrorResponse(MailgunError(_, msg)) => failure(msg)
        }
      }
    }


    "remove a member from a list " in {
      running(FakeApplication(additionalConfiguration = config)) {
        val svc = MailgunService()
        val resp = svc.removeMemberFromList(userEmail, list).await(5, TimeUnit.SECONDS).get
        resp match {
          case OkResponse(MemberResponse(_, Member(email))) => email mustEqual (userEmail)
          case ErrorResponse(MailgunError(_, msg)) => failure(msg)
        }
      }
    }




    "delete a list" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val svc = MailgunService()
        val resp = svc.deleteMailingList(listEmail).await(5, TimeUnit.SECONDS).get
        resp match {
          case OkResponse(_) => true mustEqual (true)
          case ErrorResponse(MailgunError(_, msg)) => failure(msg)
        }
      }
    }
  }

}

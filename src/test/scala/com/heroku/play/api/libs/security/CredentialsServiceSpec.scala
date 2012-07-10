package com.heroku.play.api.libs.security

import org.specs2.mutable.Specification


class CredentialsServiceSpec extends Specification {

  "Credentials Service" should {
    (1 to 100).map {
      i =>
        ("verify hashed passwords " + i) in {
          val password: String = CredentialsService.generateKey()
          val hashed = CredentialsService.hashPassword(password)
          CredentialsService.checkPasswordAgainstHash(password, hashed) mustEqual (true)
        }
    }

    val cpassword: String = CredentialsService.generateKey()
    val chashed = CredentialsService.hashPassword(cpassword)


    (1 to 100).map {
      i =>
        ("verify hashed passwords cached " + i) in {
          CredentialsService.checkPasswordAgainstHashCached(cpassword, chashed) mustEqual (true)
        }
    }


    (1 to 100).map {
      i =>
        ("mask and unmask secrets " + i) in {
          val secret = CredentialsService.generateKey()
          val mask = CredentialsService.generateKey()
          val masked = CredentialsService.maskKey(secret, mask)
          masked mustNotEqual (null)
        }
    }

    (1 to 100).map {
      i =>
        ("encrypt and decrypt credentials " + i) in {
          val credential = CredentialsService.generateKey()
          val secretKey = CredentialsService.generateKey()
          val encrypted = CredentialsService.___encryptCredential(credential, secretKey)
          CredentialsService.createCryptor(secretKey).decrypt(encrypted) mustEqual (credential)
        }
    }

    val key = CredentialsService.generateKey()

    (1 to 100).map {
      i =>
        ("encrypt and decrypt credentials cached " + i) in {
          val credential = CredentialsService.generateKey()
          val encrypted = CredentialsService.___encryptCredential(credential, key)
          CredentialsService.getCryptor(key).decrypt(encrypted) mustEqual (credential)
        }
    }

    "fake out specs2" in {
      true mustEqual (true)
    }

  }

}

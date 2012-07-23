package com.heroku.play.api.libs.security

import org.mindrot.jbcrypt.BCrypt
import sun.misc.{BASE64Decoder, BASE64Encoder}
import play.api.Play.current
import java.security.SecureRandom
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.util.concurrent.ConcurrentHashMap
import org.jasypt.encryption.StringEncryptor
import org.jasypt.salt.RandomSaltGenerator


object CredentialsService {

  //Ciphers are not threadsafe but jasypt StringEncryptors do proper synchronization
  private val encryptors = new ConcurrentHashMap[String, StringEncryptor]()
  private val hashCache = new ConcurrentHashMap[String, String]()

  def hashPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt())
  }

  def checkPasswordAgainstHash(password: String, hashed: String): Boolean = {
    BCrypt.checkpw(password, hashed)
  }

  def clearCache() {
    hashCache.clear()
  }

  /*use only for performance sensitive situations, like api calls that cant pay the latency of a bcrypt hash every request*/
  def checkPasswordAgainstHashCached(password: String, hashed: String): Boolean = {
    val okPw = hashCache.get(hashed)
    if (okPw == null) {
      if (checkPasswordAgainstHash(password, hashed)) {
        hashCache.put(hashed, password)
        true
      } else false
    } else {
      if (isEqual(okPw.getBytes("UTF-8"), password.getBytes("UTF-8"))) {
        true
      } else {
        //make invalid passwords pay the hit of calculating a bcrypt hash
        hashPassword(password)
        false
      }
    }
  }

  def isEqual(a: Array[Byte], b: Array[Byte]): Boolean = {
    if (a.length != b.length) {
      false
    } else {
      /*
      a.zip(b).foldLeft(0) {
        case (result, (ba, bb)) => result | ba ^ bb
      } == 0
      */
      var result = 0
      (0 until a.length).foreach {
        i => result |= a(i) ^ b(i)
      }
      result == 0
    }
  }

  def createCryptor(secretKey: String): StringEncryptor = {
    val cryptor = new PooledPBEStringEncryptor()
    cryptor.setPoolSize(Runtime.getRuntime.availableProcessors())
    cryptor.setProvider(new BouncyCastleProvider)
    cryptor.setAlgorithm("PBEWITHSHA256AND256BITAES-CBC-BC")
    cryptor.setPassword(secretKey)
    //this is the default but set here for explicitness
    //this results in the CBC IV being initialized with the random salt.
    cryptor.setSaltGenerator(new RandomSaltGenerator("SHA1PRNG"))
    cryptor.initialize()
    cryptor
  }

  private[security] def getCryptor(secretKey: String): StringEncryptor = {
    var encryptor = encryptors.get(secretKey)
    if (encryptor == null) {
      val created = createCryptor(secretKey)
      encryptor = encryptors.putIfAbsent(secretKey, created)
      if (encryptor == null) {
        encryptor = created
      }
    }
    encryptor
  }

  def doEncryptCredential(credential: String, secretKey: String): String = {
    getCryptor(secretKey).encrypt(credential)
  }

  def encryptCredential(credential: String, maskedSecretKeyEnvVar: String, maskPlayConfigName: String): String = {
    doEncryptCredential(credential, unmaskKey(maskedSecretKeyEnvVar, maskPlayConfigName))
  }

  def doDecryptCredential(encryptedCredential: String, key: String): String = {
    getCryptor(key).decrypt(encryptedCredential)
  }

  def decryptCredential(encryptedCredentialEnvVar: String, maskedSecretKeyEnvVar: String, maskPlayConfigName: String): String = {
    val key = unmaskKey(maskedSecretKeyEnvVar, maskPlayConfigName)
    val encryptedCredential = sys.env.get(encryptedCredentialEnvVar).getOrElse(sys.error(encryptedCredentialEnvVar + " was not found in the environment"))
    doDecryptCredential(encryptedCredential, key)
  }

  def maskKey(secretKey: String, mask: String): String = {
    val masked = xor(secretKey, mask)
    if (unxor(masked, mask) != secretKey) throw new IllegalArgumentException("unmasking the masked key failed, perhaps mask was not the same length as key?")
    masked
  }


  def unmaskKey(maskedSecretKeyEnvVar: String, maskPlayConfigName: String): String = {
    val maskedKey: String = sys.env.get(maskedSecretKeyEnvVar).getOrElse(sys.error(maskedSecretKeyEnvVar + " not found in the environment"))
    val mask = current.configuration.getString(maskPlayConfigName).getOrElse(sys.error(maskPlayConfigName + " not found in current config"))
    unxor(maskedKey, mask)
  }


  private def xor(key: String, xorStr: String): String = {
    val bytes = key.zipWithIndex.map {
      case (c, idx) => (c.toByte ^ xorStr.charAt(idx).toByte).asInstanceOf[Byte]
    }.toArray
    new BASE64Encoder().encode(bytes)
  }

  private def unxor(xored: String, xorString: String): String = {
    val decoded: Array[Byte] = new BASE64Decoder().decodeBuffer(xored)
    val unx = decoded.zipWithIndex.map {
      case (b, idx) => (b ^ xorString.charAt(idx).toByte).asInstanceOf[Byte]
    }
    new String(unx)
  }

  /**
   * util for generating random keys and masks
   * @return
   */
  def generateKey(): String = {
    import java.security.MessageDigest
    val digest = MessageDigest.getInstance("SHA-1")
    val time = System.currentTimeMillis();
    val rand = new SecureRandom().nextInt(12345678).toString
    val bytes = (time + rand).getBytes("UTF-8")
    digest.reset()
    digest.update(bytes)
    digest.digest().map(0xFF & _).map {
      "%02x".format(_)
    }.foldLeft("") {
      _ + _
    }
  }


}

package org.pytorch.serve.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.pytorch.serve.util.ConfigManager

import scala.jdk.CollectionConverters.*
class TokenAuthorization
object TokenAuthorization {
  private var apiKey: String = null
  private var managementKey: String = null
  private var inferenceKey: String = null
  private var managementExpirationTimeMinutes: Instant = null
  private var inferenceExpirationTimeMinutes: Instant = null
  private var tokenAuthEnabled = false
  private var keyFilePath: String = null
  private val secureRandom = new SecureRandom
  private val baseEncoder = Base64.getUrlEncoder
  private val bearerTokenHeaderPattern = Pattern.compile("^Bearer\\s+(\\S+)$")
  private val logger = LoggerFactory.getLogger(classOf[TokenAuthorization])

  enum TokenType:
    case INFERENCE, MANAGEMENT, TOKEN_API
//  object TokenType extends Enumeration {
//    type TokenType = Value
//    val INFERENCE, MANAGEMENT, TOKEN_API = Value
//  }

  def init(): Unit = {
    if (ConfigManager.getInstance.getDisableTokenAuthorization) {
      tokenAuthEnabled = false
      return
    }
    tokenAuthEnabled = true
    apiKey = generateKey
    keyFilePath = Paths.get(System.getProperty("user.dir"), "key_file.json").toString
    try if (generateKeyFile(TokenType.TOKEN_API)) {
      val loggingMessage = "\n######\n" + "TorchServe now enforces token authorization by default.\n" + "This requires the correct token to be provided when calling an API.\n" + "Key file located at " + keyFilePath + "\nCheck token authorization documenation for information: https://github.com/pytorch/serve/blob/master/docs/token_authorization_api.md \n" + "######\n"
      logger.info(loggingMessage)
    }
    catch {
      case e: IOException =>
        e.printStackTrace()
        logger.error("Token Authorization setup unsuccessful")
        throw new IllegalStateException("Token Authorization setup unsuccessful", e)
    }
  }

  def isEnabled: Boolean = tokenAuthEnabled

  @throws[IOException]
  def updateKeyFile(tokenType: TokenType): String = {
    var status = ""
    tokenType match {
      case TokenType.MANAGEMENT =>
        generateKeyFile(TokenType.MANAGEMENT)
      case TokenType.INFERENCE =>
        generateKeyFile(TokenType.INFERENCE)
      case _ =>
        status = "{\n\t\"Error\": " + tokenType + "\n}\n"
    }
    status
  }

  def checkTokenAuthorization(token: String, tokenType: TokenAuthorization.TokenType): Boolean = {
    var key: String = null
    var expiration: Instant = null
    tokenType match {
      case TokenType.TOKEN_API =>
        key = apiKey
        expiration = null
      case TokenType.MANAGEMENT =>
        key = managementKey
        expiration = managementExpirationTimeMinutes
      case _ =>
        key = inferenceKey
        expiration = inferenceExpirationTimeMinutes
    }
    if (token == key) if (expiration != null && isTokenExpired(expiration)) return false
    else return false
    true
  }

  def parseTokenFromBearerTokenHeader(bearerTokenHeader: String): String = {
    var token = ""
    val matcher = bearerTokenHeaderPattern.matcher(bearerTokenHeader)
    if (matcher.matches) token = matcher.group(1)
    token
  }

  private def generateKey = {
    val randomBytes = new Array[Byte](6)
    secureRandom.nextBytes(randomBytes)
    baseEncoder.encodeToString(randomBytes)
  }

  private def generateTokenExpiration = {
    val secondsToAdd = (ConfigManager.getInstance.getTimeToExpiration * 60).toLong
    Instant.now.plusSeconds(secondsToAdd)
  }

  @throws[IOException]
  private def generateKeyFile(tokenType: TokenAuthorization.TokenType): Boolean = {
    val file = new File(keyFilePath)
    if (!file.createNewFile && !file.exists) return false
    tokenType match {
      case TokenType.MANAGEMENT =>
        managementKey = generateKey
        managementExpirationTimeMinutes = generateTokenExpiration
      case TokenType.INFERENCE =>
        inferenceKey = generateKey
        inferenceExpirationTimeMinutes = generateTokenExpiration
      case _ =>
        managementKey = generateKey
        inferenceKey = generateKey
        inferenceExpirationTimeMinutes = generateTokenExpiration
        managementExpirationTimeMinutes = generateTokenExpiration
    }
    val parentObject = new JsonObject
    val managementObject = new JsonObject
    managementObject.addProperty("key", managementKey)
    managementObject.addProperty("expiration time", managementExpirationTimeMinutes.toString)
    parentObject.add("management", managementObject)
    val inferenceObject = new JsonObject
    inferenceObject.addProperty("key", inferenceKey)
    inferenceObject.addProperty("expiration time", inferenceExpirationTimeMinutes.toString)
    parentObject.add("inference", inferenceObject)
    val apiObject = new JsonObject
    apiObject.addProperty("key", apiKey)
    parentObject.add("API", apiObject)
    Files.write(Paths.get(keyFilePath), new GsonBuilder().setPrettyPrinting.create.toJson(parentObject).getBytes(StandardCharsets.UTF_8))
    if (!setFilePermissions) {
      try Files.delete(Paths.get(keyFilePath))
      catch {
        case e: IOException =>
          return false
      }
      return false
    }
    true
  }

  private def setFilePermissions: Boolean = {
    val path = Paths.get(keyFilePath)
    try {
      val permissions = PosixFilePermissions.fromString("rw-------")
      Files.setPosixFilePermissions(path, permissions)
    } catch {
      case e: Exception =>
        return false
    }
    true
  }

  private def isTokenExpired(expirationTime: Instant) = !Instant.now.isBefore(expirationTime)
}
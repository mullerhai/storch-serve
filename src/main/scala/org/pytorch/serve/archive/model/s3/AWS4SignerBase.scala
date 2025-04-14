package org.pytorch.serve.archive.model.s3

import java.io.UnsupportedEncodingException
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util
import java.util.Collections
import java.util.SimpleTimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Common methods and properties for all AWS4 signer variants */
object AWS4SignerBase {
  /** SHA256 hash of an empty request body * */
  val EMPTY_BODY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
  val SCHEME = "AWS4"
  val ALGORITHM = "HMAC-SHA256"
  val TERMINATOR = "aws4_request"
  /** format strings for the date/time and date stamps required during signing * */
  val ISO8601BASICFORMAT = "yyyyMMdd'T'HHmmss'Z'"
  val DATASTRINGFORMAT = "yyyyMMdd"

  /**
   * Returns the canonical collection of header names that will be included in the signature. For
   * AWS4, all header names must be included in the process in sorted canonicalized order.
   */
  def getCanonicalizeHeaderNames(headers: util.Map[String, String]): String = {
    val sortedHeaders = new util.ArrayList[String]
    sortedHeaders.addAll(headers.keySet)
    Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER)
    val buffer = new StringBuilder
    import scala.jdk.CollectionConverters._
    for (header <- sortedHeaders.asScala) {
      if (buffer.length > 0) buffer.append(';')
      buffer.append(header.toLowerCase)
    }
    buffer.toString
  }

  /**
   * Computes the canonical headers with values for the request. For AWS4, all headers must be
   * included in the signing process.
   */
   def getCanonicalizedHeaderString(headers: util.Map[String, String]): String = {
    if (headers == null || headers.isEmpty) return ""
    // step1: sort the headers by case-insensitive order
    val sortedHeaders = new util.ArrayList[String]
    sortedHeaders.addAll(headers.keySet)
    Collections.sort(sortedHeaders, String.CASE_INSENSITIVE_ORDER)
    // step2: form the canonical header:value entries in sorted order.
    // Multiple white spaces in the values should be compressed to a single
    // space.
    val buffer = new StringBuilder
    import scala.jdk.CollectionConverters._
    for (key <- sortedHeaders.asScala) {
      buffer.append(key.toLowerCase.replaceAll("\\s+", " ") + ":" + headers.get(key).replaceAll("\\s+", " "))
      buffer.append('\n')
    }
    buffer.toString
  }

  /**
   * Returns the canonical request string to go into the signer process; this consists of several
   * canonical sub-parts.
   *
   * @return
   */
  @throws[UnsupportedEncodingException]
  def getCanonicalRequest(endpoint: URL, httpMethod: String, queryParameters: String, canonicalizedHeaderNames: String, canonicalizedHeaders: String, bodyHash: String): String = httpMethod + "\n" + getCanonicalizedResourcePath(endpoint) + "\n" + queryParameters + "\n" + canonicalizedHeaders + "\n" + canonicalizedHeaderNames + "\n" + bodyHash

  /** Returns the canonicalized resource path for the service endpoint. */
  @throws[UnsupportedEncodingException]
  def getCanonicalizedResourcePath(endpoint: URL): String = {
    if (endpoint == null) return "/"
    val path = endpoint.getPath
    if (path == null || path.isEmpty) return "/"
    val encodedPath = HttpUtils.urlEncode(path, true)
    if (encodedPath.startsWith("/")) encodedPath
    else "/".concat(encodedPath)
  }

  /**
   * Examines the specified query string parameters and returns a canonicalized form.
   *
   * <p>The canonicalized query string is formed by first sorting all the query string parameters,
   * then URI encoding both the key and value and then joining them, in order, separating key
   * value pairs with an '&'.
   *
   * @param parameters The query string parameters to be canonicalized.
   * @return A canonicalized form for the specified query string parameters.
   */
  @throws[UnsupportedEncodingException]
  def getCanonicalizedQueryString(parameters: util.Map[String, String]): String = {
    if (parameters == null || parameters.isEmpty) return ""
    val sorted = new util.TreeMap[String, String]
    var pairs = parameters.entrySet.iterator
    while (pairs.hasNext) {
      val pair = pairs.next
      val key = pair.getKey
      val value = pair.getValue
      sorted.put(HttpUtils.urlEncode(key, false), HttpUtils.urlEncode(value, false))
    }
    val builder = new StringBuilder
    pairs = sorted.entrySet.iterator
    while (pairs.hasNext) {
      val pair = pairs.next
      builder.append(pair.getKey)
      builder.append('=')
      builder.append(pair.getValue)
      if (pairs.hasNext) builder.append('&')
    }
    builder.toString
  }

  def getStringToSign(scheme: String, algorithm: String, dateTime: String, scope: String, canonicalRequest: String): String = scheme + "-" + algorithm + "\n" + dateTime + "\n" + scope + "\n" + BinaryUtils.toHex(hash(canonicalRequest))

  /** Hashes the string contents (assumed to be UTF-8) using the SHA-256 algorithm. */
  def hash(text: String): Array[Byte] = try {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(text.getBytes("UTF-8"))
    md.digest
  } catch {
    case e: Exception =>
      throw new IllegalStateException("Unable to compute hash while signing request: " + e.getMessage, e)
  }

  /** Hashes the byte array using the SHA-256 algorithm. */
  def hash(data: Array[Byte]): Array[Byte] = try {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(data)
    md.digest
  } catch {
    case e: Exception =>
      throw new IllegalStateException("Unable to compute hash while signing request: " + e.getMessage, e)
  }

  def sign(stringData: String, key: Array[Byte], algorithm: String): Array[Byte] = try {
    val data = stringData.getBytes("UTF-8")
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data)
  } catch {
    case e: Exception =>
      throw new IllegalStateException("Unable to calculate a request signature: " + e.getMessage, e)
  }
}

class AWS4SignerBase(var endpointUrl: URL, var httpMethod: String, var serviceName: String, var regionName: String){

/**
 * Create a new AWS V4 signer.
 *
 * @param endpointUrl The service endpoint, including the path to any resource.
 * @param httpMethod  The HTTP verb for the request, e.g. GET.
 * @param serviceName The signing name of the service, e.g. 's3'.
 * @param regionName  The system name of the AWS region associated with the endpoint, e.g.
 *                    us-east-1.
 */
  var dateTimeFormat = new SimpleDateFormat(AWS4SignerBase.ISO8601BASICFORMAT)
  dateTimeFormat.setTimeZone(new SimpleTimeZone(0, "UTC"))
  var dateStampFormat = new SimpleDateFormat(AWS4SignerBase.DATASTRINGFORMAT)
  dateStampFormat.setTimeZone(new SimpleTimeZone(0, "UTC"))
//  final protected var dateTimeFormat: SimpleDateFormat = null
//  final protected var dateStampFormat: SimpleDateFormat = null
}
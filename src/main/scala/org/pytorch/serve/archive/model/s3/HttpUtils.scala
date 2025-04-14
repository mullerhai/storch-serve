package org.pytorch.serve.archive.model.s3

import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.FileAlreadyExistsException
import java.util
import org.apache.commons.io.FileUtils
import org.pytorch.serve.archive.utils.ArchiveUtils
import org.pytorch.serve.archive.utils.InvalidArchiveURLException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.pytorch.serve.archive.model.s3._

case class HttpUtils()
/** Various Http helper routines */
object HttpUtils {
  private val logger = LoggerFactory.getLogger(classOf[HttpUtils])

  /** Copy model from S3 url to local model store */
  @throws[FileAlreadyExistsException]
  @throws[IOException]
  @throws[InvalidArchiveURLException]
  def copyURLToFile(allowedUrls: util.List[String], 
                    url: String, 
                    modelLocation: File, 
                    s3SseKmsEnabled: Boolean, 
                    archiveName: String): Boolean = {
    if (ArchiveUtils.validateURL(allowedUrls, url)) {
      if (modelLocation.exists) throw new FileAlreadyExistsException(archiveName)
      if (archiveName.contains("/") || archiveName.contains("\\")) throw new IOException("Security alert slash or backslash appear in archiveName:" + archiveName)
      // for a simple GET, we have no body so supply the precomputed 'empty' hash
      var headers: util.Map[String, String] = null
      if (s3SseKmsEnabled) {
        val awsAccessKey = System.getenv("AWS_ACCESS_KEY_ID")
        val awsSecretKey = System.getenv("AWS_SECRET_ACCESS_KEY")
        val regionName = System.getenv("AWS_DEFAULT_REGION")
        if (regionName.isEmpty || awsAccessKey.isEmpty || awsSecretKey.isEmpty) throw new IOException("Miss environment variables " + "AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY or AWS_DEFAULT_REGION")
        val connection = new URL(url).openConnection.asInstanceOf[HttpURLConnection]
        headers = new util.HashMap[String, String]
        headers.put("x-amz-content-sha256", AWS4SignerBase.EMPTY_BODY_SHA256)
        val signer = new AWS4SignerForAuthorizationHeader(connection.getURL, "GET", "s3", regionName)
        val authorization = signer.computeSignature(headers, null, // no query parameters
          AWS4SignerBase.EMPTY_BODY_SHA256, awsAccessKey, awsSecretKey)
        // place the computed signature into a formatted 'Authorization' header
        // and call S3
        headers.put("Authorization", authorization)
        setHttpConnection(connection, "GET", headers)
        try FileUtils.copyInputStreamToFile(connection.getInputStream, modelLocation)
        finally if (connection != null) connection.disconnect()
      }
      else {
        val endpointUrl = new URL(url)
        FileUtils.copyURLToFile(endpointUrl, modelLocation)
      }
    }
    false
  }

  @throws[IOException]
  def setHttpConnection(connection: HttpURLConnection, 
                        httpMethod: String, 
                        headers: util.Map[String, String]): Unit = {
    connection.setRequestMethod(httpMethod)
    if (headers != null) {
      import scala.jdk.CollectionConverters._
      for (headerKey <- headers.keySet.asScala) {
        connection.setRequestProperty(headerKey, headers.get(headerKey))
      }
    }
  }

  @throws[UnsupportedEncodingException]
  def urlEncode(url: String, keepPathSlash: Boolean): String = {
    var encoded: String = null
    try encoded = URLEncoder.encode(url, "UTF-8")
    catch {
      case e: UnsupportedEncodingException =>
        logger.error("UTF-8 encoding is not supported.", e)
        throw e
    }
    if (keepPathSlash) encoded = encoded.replace("%2F", "/")
    encoded
  }
}

//final class HttpUtils private {
//}
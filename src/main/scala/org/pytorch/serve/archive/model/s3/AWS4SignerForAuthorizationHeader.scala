package org.pytorch.serve.archive.model.s3

import java.io.UnsupportedEncodingException
import java.net.URL
import java.util
import java.util.Date
import scala.collection.mutable.{ListBuffer, TreeMap, Map as MutableMap}
/**
 * Sample AWS4 signer demonstrating how to sign requests to Amazon S3 using an 'Authorization'
 * header.
 */
class AWS4SignerForAuthorizationHeader(endpointUrl: URL, httpMethod: String, serviceName: String, regionName: String) extends AWS4SignerBase(endpointUrl, httpMethod, serviceName, regionName) {
  /**
   * Computes an AWS4 signature for a request, ready for inclusion as an 'Authorization' header.
   *
   * @param headers         The request headers; 'Host' and 'X-Amz-Date' will be added to this set.
   * @param queryParameters Any query parameters that will be added to the endpoint. The
   *                        parameters should be specified in canonical format.
   * @param bodyHash        Precomputed SHA256 hash of the request body content; this value should also
   *                        be set as the header 'X-Amz-Content-SHA256' for non-streaming uploads.
   * @param awsAccessKey    The user's AWS Access Key.
   * @param awsSecretKey    The user's AWS Secret Key.
   * @return The computed authorization string for the request. This value needs to be set as the
   *         header 'Authorization' on the subsequent HTTP request.
   */
  @throws[UnsupportedEncodingException]
  def computeSignature(headers: MutableMap[String, String],
                       queryParameters: Map[String, String],
                       bodyHash: String, 
                       awsAccessKey: String, 
                       awsSecretKey: String): String = {
    // first get the date and time for the subsequent request, and convert
    // to ISO 8601 format for use in signature generation
    val now = new Date
    val dateTimeStamp = dateTimeFormat.format(now)
    // update the headers with required 'x-amz-date' and 'host' values
    headers.put("x-amz-date", dateTimeStamp)
    val hostHeader = new StringBuilder(endpointUrl.getHost)
    val port = endpointUrl.getPort
    if (port > -1) hostHeader.append(":" + Integer.toString(port))
    headers.put("Host", hostHeader.toString)
    // canonicalize the headers; we need the set of header names as well as the
    // names and values to go into the signature process
    val canonicalizedHeaderNames = AWS4SignerBase.getCanonicalizeHeaderNames(headers)
    val canonicalizedHeaders = AWS4SignerBase.getCanonicalizedHeaderString(headers)
    // if any query string parameters have been supplied, canonicalize them
    val canonicalizedQueryParameters = AWS4SignerBase.getCanonicalizedQueryString(queryParameters)
    // canonicalize the various components of the request
    val canonicalRequest = AWS4SignerBase.getCanonicalRequest(endpointUrl, httpMethod, canonicalizedQueryParameters, canonicalizedHeaderNames, canonicalizedHeaders, bodyHash)
    // construct the string to be signed
    val dateStamp = dateStampFormat.format(now)
    val scope = dateStamp + "/" + regionName + "/" + serviceName + "/" + AWS4SignerBase.TERMINATOR
    val stringToSign = AWS4SignerBase.getStringToSign(AWS4SignerBase.SCHEME, AWS4SignerBase.ALGORITHM, dateTimeStamp, scope, canonicalRequest)
    // compute the signing key
    val kSecret = (AWS4SignerBase.SCHEME + awsSecretKey).getBytes
    val kDate = AWS4SignerBase.sign(dateStamp, kSecret, "HmacSHA256")
    val kRegion = AWS4SignerBase.sign(regionName, kDate, "HmacSHA256")
    val kService = AWS4SignerBase.sign(serviceName, kRegion, "HmacSHA256")
    val kSigning = AWS4SignerBase.sign(AWS4SignerBase.TERMINATOR, kService, "HmacSHA256")
    val signature = AWS4SignerBase.sign(stringToSign, kSigning, "HmacSHA256")
    val credentialsAuthorizationHeader = "Credential=" + awsAccessKey + "/" + scope
    val signedHeadersAuthorizationHeader = "SignedHeaders=" + canonicalizedHeaderNames
    val signatureAuthorizationHeader = "Signature=" + BinaryUtils.toHex(signature)
    AWS4SignerBase.SCHEME + "-" + AWS4SignerBase.ALGORITHM + " " + credentialsAuthorizationHeader + ", " + signedHeadersAuthorizationHeader + ", " + signatureAuthorizationHeader
  }
}
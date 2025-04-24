package org.pytorch.serve.util.messages

import java.util
import scala.collection.mutable

class Predictions {
  private var requestId: String = null
  private var statusCode = 0
  private var reasonPhrase: String = null
  private var contentType: String = null
  private var headers: mutable.Map[String, String] = mutable.Map.empty
  private var resp: Array[Byte] = null

  def getHeaders: Map[String, String] = headers.toMap

  def setHeaders(headers: Map[String, String]): Unit = {
    //    headers.toList.map((k,v) =>this.headers.+)
    this.headers.++=(headers) // headers
  }

  def getRequestId: String = requestId

  def setRequestId(requestId: String): Unit = {
    this.requestId = requestId
  }

  def getResp: Array[Byte] = resp

  def setResp(resp: Array[Byte]): Unit = {
    this.resp = resp.clone
  }

  def getContentType: String = contentType

  def setStatusCode(statusCode: Int): Unit = {
    this.statusCode = statusCode
  }

  def setContentType(contentType: String): Unit = {
    this.contentType = contentType
  }

  def getStatusCode: Int = statusCode

  def getReasonPhrase: String = reasonPhrase

  def setReasonPhrase(reasonPhrase: String): Unit = {
    this.reasonPhrase = reasonPhrase
  }
}
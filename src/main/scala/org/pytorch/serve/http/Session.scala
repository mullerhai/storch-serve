package org.pytorch.serve.http

import io.netty.handler.codec.http.HttpRequest
import java.util.UUID

class Session {
  private var requestId: String = null
  private var remoteIp: String = null
  private var method: String = null
  private var uri: String = null
  private var protocol: String = null
  private var code = 0
  private var startTime = 0L

  def this(remoteIp: String, request: HttpRequest) ={
    this()
    this.remoteIp = remoteIp
    this.uri = request.uri
    if (request.decoderResult.isSuccess) {
      method = request.method.name
      protocol = request.protocolVersion.text
    }
    else {
      method = "GET"
      protocol = "HTTP/1.1"
    }
    requestId = UUID.randomUUID.toString
    startTime = System.currentTimeMillis
  }

  def this(remoteIp: String, gRPCMethod: String)= {
    this()
    this.remoteIp = remoteIp
    method = "gRPC"
    protocol = "HTTP/2.0"
    this.uri = gRPCMethod
    requestId = UUID.randomUUID.toString
    startTime = System.currentTimeMillis
  }

  def getRequestId: String = requestId

  def setCode(code: Int): Unit = {
    this.code = code
  }

  override def toString: String = {
    val duration = System.currentTimeMillis - startTime
    remoteIp + " \"" + method + " " + uri + ' ' + protocol + "\" " + code + ' ' + duration
  }
}
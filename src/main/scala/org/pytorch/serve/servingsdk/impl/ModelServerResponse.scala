package org.pytorch.serve.servingsdk.impl

import io.netty.buffer.ByteBufOutputStream
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import java.io.OutputStream
import org.pytorch.serve.servingsdk.http.Response

class ModelServerResponse(private var response: FullHttpResponse) extends Response {
  override def setStatus(i: Int): Unit = {
    response.setStatus(HttpResponseStatus.valueOf(i))
  }

  override def setStatus(i: Int, s: String): Unit = {
    response.setStatus(new HttpResponseStatus(i, s))
  }

  override def setHeader(k: String, v: String): Unit = {
    response.headers.set(k, v)
  }

  override def addHeader(k: String, v: String): Unit = {
    response.headers.add(k, v)
  }

  override def setContentType(contentType: String): Unit = {
    response.headers.set(HttpHeaderNames.CONTENT_TYPE, contentType)
  }

  override def getOutputStream = new ByteBufOutputStream(response.content)
}
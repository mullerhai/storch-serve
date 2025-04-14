package org.pytorch.serve.servingsdk.impl

import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.ByteArrayInputStream
import java.util
import org.pytorch.serve.servingsdk.http.Request
import org.pytorch.serve.util.NettyUtils

class ModelServerRequest(private var req: FullHttpRequest, private var decoder: QueryStringDecoder) extends Request {
  override def getHeaderNames = new util.ArrayList[String](req.headers.names)

  override def getRequestURI: String = req.uri

  override def getParameterMap: util.Map[String, util.List[String]] = decoder.parameters

  override def getParameter(k: String): util.List[String] = decoder.parameters.get(k)

  override def getContentType: String = HttpUtil.getMimeType(req).toString

  override def getInputStream = new ByteArrayInputStream(NettyUtils.getBytes(req.content))
}
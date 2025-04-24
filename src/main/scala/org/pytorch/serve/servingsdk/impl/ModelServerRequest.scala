package org.pytorch.serve.servingsdk.impl

import io.netty.handler.codec.http.{FullHttpRequest, HttpUtil, QueryStringDecoder}
import org.pytorch.serve.servingsdk.http.Request
import org.pytorch.serve.util.NettyUtils

import java.io.ByteArrayInputStream
import java.util
import scala.jdk.CollectionConverters.*
class ModelServerRequest(private var req: FullHttpRequest, private var decoder: QueryStringDecoder) extends Request {
  override def getHeaderNames: util.List[String] = req.headers.names.asScala.toList.asJava

  override def getRequestURI: String = req.uri

  override def getParameterMap: util.Map[String, util.List[String]] = decoder.parameters //.asScala.map((k,v)=>(k,v.asScala.toList)).toMap

  override def getParameter(k: String): util.List[String] = decoder.parameters.get(k)

  override def getContentType: String = HttpUtil.getMimeType(req).toString

  override def getInputStream = new ByteArrayInputStream(NettyUtils.getBytes(req.content))
}
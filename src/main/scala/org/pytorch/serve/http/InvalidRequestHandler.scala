package org.pytorch.serve.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import org.pytorch.serve.archive.model.ModelException

class InvalidRequestHandler extends HttpRequestHandlerChain {
  @throws[ModelException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    throw new ResourceNotFoundException
  }
}
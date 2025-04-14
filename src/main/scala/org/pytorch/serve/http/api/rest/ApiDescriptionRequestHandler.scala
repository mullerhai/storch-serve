package org.pytorch.serve.http.api.rest

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.http.HttpRequestHandlerChain
import org.pytorch.serve.http.MethodNotAllowedException
import org.pytorch.serve.openapi.OpenApiUtils
import org.pytorch.serve.util.ConnectorType
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.wlm.WorkerInitializationException

class ApiDescriptionRequestHandler(private var connectorType: ConnectorType) extends HttpRequestHandlerChain {
  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    if (isApiDescription(segments)) {
      val path = decoder.path
      if (("/" == path && HttpMethod.OPTIONS == req.method) || (segments.length == 2 && segments(1) == "api-description")) {
        handleApiDescription(ctx)
        return
      }
      throw new MethodNotAllowedException
    }
    else chain.handleRequest(ctx, req, decoder, segments)
  }

  private def isApiDescription(segments: Array[String]) = segments.length == 0 || (segments.length == 2 && segments(1) == "api-description")

  private def handleApiDescription(ctx: ChannelHandlerContext): Unit = {
    NettyUtils.sendJsonResponse(ctx, OpenApiUtils.listApis(connectorType))
  }
}
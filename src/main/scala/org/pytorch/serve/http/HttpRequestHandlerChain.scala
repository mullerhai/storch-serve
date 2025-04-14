package org.pytorch.serve.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import java.io.IOException
import java.util
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.servingsdk.ModelServerEndpoint
import org.pytorch.serve.servingsdk.ModelServerEndpointException
import org.pytorch.serve.servingsdk.impl.ModelServerContext
import org.pytorch.serve.servingsdk.impl.ModelServerRequest
import org.pytorch.serve.servingsdk.impl.ModelServerResponse
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.wlm.ModelManager
import org.pytorch.serve.wlm.WorkerInitializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object HttpRequestHandlerChain {
  private val logger = LoggerFactory.getLogger(classOf[HttpRequestHandler])
}

abstract class HttpRequestHandlerChain {
  protected var endpointMap: util.Map[String, ModelServerEndpoint] = null
  protected var chain: HttpRequestHandlerChain = null

  def this(map: util.Map[String, ModelServerEndpoint]) ={
    this()
    endpointMap = map
  }

  def setNextHandler(nextHandler: HttpRequestHandlerChain): HttpRequestHandlerChain = {
    chain = nextHandler
    chain
  }

  @throws[ModelNotFoundException]
  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit

  @throws[IOException]
  private def run(endpoint: ModelServerEndpoint, req: FullHttpRequest, rsp: FullHttpResponse, decoder: QueryStringDecoder, method: String): Unit = {
    method match {
      case "GET" =>
        endpoint.doGet(new ModelServerRequest(req, decoder), new ModelServerResponse(rsp), new ModelServerContext)
      case "PUT" =>
        endpoint.doPut(new ModelServerRequest(req, decoder), new ModelServerResponse(rsp), new ModelServerContext)
      case "DELETE" =>
        endpoint.doDelete(new ModelServerRequest(req, decoder), new ModelServerResponse(rsp), new ModelServerContext)
      case "POST" =>
        endpoint.doPost(new ModelServerRequest(req, decoder), new ModelServerResponse(rsp), new ModelServerContext)
      case _ =>
        throw new ServiceUnavailableException("Invalid HTTP method received")
    }
  }

  protected def handleCustomEndpoint(ctx: ChannelHandlerContext, req: FullHttpRequest, segments: Array[String], decoder: QueryStringDecoder): Unit = {
    val endpoint = endpointMap.get(segments(1))
    val r:Runnable = () => {
      val start = System.currentTimeMillis
      val rsp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, true)
      try {
        run(endpoint, req, rsp, decoder, req.method.toString)
        NettyUtils.sendHttpResponse(ctx, rsp, true)
        HttpRequestHandlerChain.logger.info("Running \"{}\" endpoint took {} ms", segments(0), System.currentTimeMillis - start)
      } catch {
        case me: ModelServerEndpointException =>
          NettyUtils.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, me)
          HttpRequestHandlerChain.logger.error("Error thrown by the model endpoint plugin.", me)
        case oom: OutOfMemoryError =>
          NettyUtils.sendError(ctx, HttpResponseStatus.INSUFFICIENT_STORAGE, oom, "Out of memory")
        case ioe: IOException =>
          NettyUtils.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, ioe, "I/O error while running the custom endpoint")
          HttpRequestHandlerChain.logger.error("I/O error while running the custom endpoint.", ioe)
        case e: Throwable =>
          NettyUtils.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, e, "Unknown exception")
          HttpRequestHandlerChain.logger.error("Unknown exception", e)
      }
    }
    ModelManager.getInstance.submitTask(r)
  }
}
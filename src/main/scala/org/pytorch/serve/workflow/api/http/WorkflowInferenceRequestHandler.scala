package org.pytorch.serve.workflow.api.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.multipart.HttpDataFactory
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder

import java.util
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.archive.workflow.WorkflowNotFoundException
import org.pytorch.serve.http.BadRequestException
import org.pytorch.serve.http.HttpRequestHandlerChain
import org.pytorch.serve.http.ResourceNotFoundException
import org.pytorch.serve.http.StatusResponse
import org.pytorch.serve.http.api.rest.InferenceRequestHandler
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.util.messages.InputParameter
import org.pytorch.serve.util.messages.RequestInput
import org.pytorch.serve.wlm.WorkerInitializationException
import org.pytorch.serve.workflow.WorkflowManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.pytorch.serve.util.ConfigManager

import scala.jdk.CollectionConverters.*
/**
 * A class handling inbound HTTP requests to the workflow inference API.
 *
 * <p>This class
 */
object WorkflowInferenceRequestHandler {
  private val logger = LoggerFactory.getLogger(classOf[InferenceRequestHandler])

  private def parseRequest(ctx: ChannelHandlerContext, req: FullHttpRequest) = {
    val requestId = NettyUtils.getRequestId(ctx.channel)
    val inputData = new RequestInput(requestId)
    val contentType = HttpUtil.getMimeType(req)
//    import scala.collection.JavaConversions._
    for (entry <- req.headers.entries.asScala) {
      inputData.updateHeaders(entry.getKey, entry.getValue)
    }
    if (HttpPostRequestDecoder.isMultipart(req) || HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.contentEqualsIgnoreCase(contentType)) {
      val factory = new DefaultHttpDataFactory(ConfigManager.getInstance.getMaxRequestSize)
      val form = new HttpPostRequestDecoder(factory, req)
      try while (form.hasNext) inputData.addParameter(NettyUtils.getFormData(form.next))
      catch {
        case ignore: HttpPostRequestDecoder.EndOfDataDecoderException =>
          logger.trace("End of multipart items.")
      } finally {
        form.cleanFiles()
        form.destroy()
      }
    }
    else {
      val content = NettyUtils.getBytes(req.content)
      inputData.addParameter(new InputParameter("body", content, contentType))
    }
    inputData
  }
}

class WorkflowInferenceRequestHandler

/** Creates a new {@code WorkflowInferenceRequestHandler} instance. */
  extends HttpRequestHandlerChain {
  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    if ("wfpredict".equalsIgnoreCase(segments(1))) {
      if (segments.length < 3) throw new ResourceNotFoundException
      handlePredictions(ctx, req, segments)
    }
    else chain.handleRequest(ctx, req, decoder, segments)
  }

  @throws[WorkflowNotFoundException]
  private def handlePredictions(ctx: ChannelHandlerContext, req: FullHttpRequest, segments: Array[String]): Unit = {
    val input = WorkflowInferenceRequestHandler.parseRequest(ctx, req)
    WorkflowInferenceRequestHandler.logger.info(input.toString)
    val wfName = segments(2)
    if (wfName == null) throw new BadRequestException("Parameter workflow_name is required.")
    WorkflowManager.getInstance.predict(ctx, wfName, input)
  }

  private def sendResponse(ctx: ChannelHandlerContext, statusResponse: StatusResponse): Unit = {
    if (statusResponse != null) if (statusResponse.getHttpResponseCode >= 200 && statusResponse.getHttpResponseCode < 300) NettyUtils.sendJsonResponse(ctx, statusResponse)
    else {
      // Re-map HTTPURLConnections HTTP_ENTITY_TOO_LARGE to Netty's INSUFFICIENT_STORAGE
      val httpResponseStatus = statusResponse.getHttpResponseCode
      NettyUtils.sendError(ctx, HttpResponseStatus.valueOf(if (httpResponseStatus == 413) 507
      else httpResponseStatus), statusResponse.getE)
    }
  }
}
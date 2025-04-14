package org.pytorch.serve.http.api.rest

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.http.HttpRequestHandlerChain
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.wlm.WorkerInitializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
/**
 * A class handling inbound HTTP requests to the Kserve's Open Inference Protocol API.
 *
 * <p>This class
 */
object OpenInferenceProtocolRequestHandler {
  private val logger = LoggerFactory.getLogger(classOf[OpenInferenceProtocolRequestHandler])
  private val TS_VERSION_FILE_PATH = "ts/version.txt"
  private val SERVER_METADATA_API = "/v2"
  private val SERVER_LIVE_API = "/v2/health/live"
  private val SERVER_READY_API = "/v2/health/ready"
}

class OpenInferenceProtocolRequestHandler extends HttpRequestHandlerChain {
  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    val concatenatedSegments =  segments.mkString("//")
    OpenInferenceProtocolRequestHandler.logger.info("Handling OIP http requests")
    if (concatenatedSegments == OpenInferenceProtocolRequestHandler.SERVER_READY_API) {
      // for serve ready check
      val response = new JsonObject
      response.addProperty("ready", true)
      NettyUtils.sendJsonResponse(ctx, response)
    }
    else if (concatenatedSegments == OpenInferenceProtocolRequestHandler.SERVER_LIVE_API) {
      // for serve live check
      val response = new JsonObject
      response.addProperty("live", true)
      NettyUtils.sendJsonResponse(ctx, response)
    }
    else if (concatenatedSegments == OpenInferenceProtocolRequestHandler.SERVER_METADATA_API) {
      // For fetch server metadata
      val supportedExtensions = new JsonArray
      val response = new JsonObject
      val tsVersion = ConfigManager.getInstance.getVersion
      response.addProperty("name", "Torchserve")
      response.addProperty("version", tsVersion)
      supportedExtensions.add("kserve")
      supportedExtensions.add("kubeflow")
      response.add("extenstion", supportedExtensions)
      NettyUtils.sendJsonResponse(ctx, response)
    }
    else if (segments.length > 5 && concatenatedSegments.contains("/versions")) {
      // As of now kserve not implemented versioning, we just throws not implemented.
      val response = new JsonObject
      response.addProperty("error", "Model versioning is not yet supported.")
      NettyUtils.sendJsonResponse(ctx, response, HttpResponseStatus.NOT_IMPLEMENTED)
    }
    else chain.handleRequest(ctx, req, decoder, segments)
  }
}
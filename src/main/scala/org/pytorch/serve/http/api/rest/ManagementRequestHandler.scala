package org.pytorch.serve.http.api.rest

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.{ModelException, ModelNotFoundException, ModelVersionNotFoundException}
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.http.*
import org.pytorch.serve.http.messages.{DescribeModelResponse, KFV1ModelReadyResponse, ListModelsResponse, RegisterModelRequest}
import org.pytorch.serve.job.RestJob
import org.pytorch.serve.openapi.OpenApiUtils
import org.pytorch.serve.servingsdk.ModelServerEndpoint
import org.pytorch.serve.util.messages.{RequestInput, WorkerCommands}
import org.pytorch.serve.util.{ApiUtils, ConfigManager, JsonUtils, NettyUtils}
import org.pytorch.serve.wlm.{Model, ModelManager, WorkerInitializationException, WorkerThread}

import java.util
import java.util.concurrent.ExecutionException
import scala.jdk.CollectionConverters.*
/**
 * A class handling inbound HTTP requests to the workflow management API.
 * /** Creates a new {@code ManagementRequestHandler} instance. */
 * <p>This class
 */
class ManagementRequestHandler(ep: Map[String, ModelServerEndpoint]) extends HttpRequestHandlerChain {
  endpointMap = ep
//  configManager = ConfigManager.getInstance
  private var configManager: ConfigManager = ConfigManager.getInstance

  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    if (isManagementReq(segments)) if (endpointMap.getOrElse(segments(1), null) != null) handleCustomEndpoint(ctx, req, segments, decoder)
    else {
      if (!("models" == segments(1))) throw new ResourceNotFoundException
      val method = req.method
      if (segments.length < 3) {
        if (HttpMethod.GET == method) {
          handleListModels(ctx, decoder)
          return
        }
        else if (HttpMethod.POST == method && configManager.isModelApiEnabled) {
          handleRegisterModel(ctx, decoder, req)
          return
        }
        throw new MethodNotAllowedException
      }
      var modelVersion: String = null
      if (segments.length == 4) modelVersion = segments(3)
      if (HttpMethod.GET == method) handleDescribeModel(ctx, req, segments(2), modelVersion, decoder)
      else if (HttpMethod.PUT == method) if (segments.length == 5 && "set-default" == segments(4)) setDefaultModelVersion(ctx, segments(2), segments(3))
      else handleScaleModel(ctx, decoder, segments(2), modelVersion)
      else if (HttpMethod.DELETE == method && configManager.isModelApiEnabled) handleUnregisterModel(ctx, segments(2), modelVersion)
      else if (HttpMethod.OPTIONS == method) {
        val modelManager = ModelManager.getInstance
        val model = modelManager.getModel(segments(2), modelVersion)
        if (model == null) throw new ModelNotFoundException("Model not found: " + segments(2))
        val resp = OpenApiUtils.getModelManagementApi(model)
        NettyUtils.sendJsonResponse(ctx, resp)
      }
      else throw new MethodNotAllowedException
    }
    else if (isKFV1ManagementReq(segments)) {
      val modelVersion: String = null
      val modelName = segments(3).split(":")(0)
      val method = req.method
      if (HttpMethod.GET == method) handleKF1ModelReady(ctx, modelName, modelVersion)
      else throw new MethodNotAllowedException
    }
    else chain.handleRequest(ctx, req, decoder, segments)
  }

  private def isManagementReq(segments: Array[String]) = segments.length == 0 || ((segments.length >= 2 && segments.length <= 4) && segments(1) == "models") || (segments.length == 5 && "set-default" == segments(4)) || endpointMap.contains(segments(1))

  private def isKFV1ManagementReq(segments: Array[String]) = segments.length == 4 && "v1" == segments(1) && "models" == segments(2)

  private def handleListModels(ctx: ChannelHandlerContext, decoder: QueryStringDecoder): Unit = {
    val limit = NettyUtils.getIntParameter(decoder, "limit", 100)
    val pageToken = NettyUtils.getIntParameter(decoder, "next_page_token", 0)
    val list = ApiUtils.getModelList(limit, pageToken)
    NettyUtils.sendJsonResponse(ctx, list)
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  private def handleDescribeModel(ctx: ChannelHandlerContext, req: FullHttpRequest, modelName: String, modelVersion: String, decoder: QueryStringDecoder): Unit = {
    val customizedMetadata = java.lang.Boolean.parseBoolean(NettyUtils.getParameter(decoder, "customized", "false"))
    if ("all" == modelVersion || !customizedMetadata) {
      val resp = ApiUtils.getModelDescription(modelName, modelVersion)
      NettyUtils.sendJsonResponse(ctx, resp)
    }
    else {
      val requestId = NettyUtils.getRequestId(ctx.channel)
      val input = new RequestInput(requestId)
//      import scala.collection.JavaConversions._
      for (entry <- req.headers.entries.asScala) {
        input.updateHeaders(entry.getKey, entry.getValue)
      }
      input.updateHeaders("describe", "True")
      val job = new RestJob(ctx, modelName, modelVersion, WorkerCommands.DESCRIBE, input)
      if (!ModelManager.getInstance.addJob(job)) {
        val responseMessage = ApiUtils.getDescribeErrorResponseMessage(modelName)
        throw new ServiceUnavailableException(responseMessage)
      }
    }
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  private def handleKF1ModelReady(ctx: ChannelHandlerContext, modelName: String, modelVersion: String): Unit = {
    val modelManager = ModelManager.getInstance
    val model = modelManager.getModel(modelName, modelVersion)
    if (model == null) throw new ModelNotFoundException("Model not found: " + modelName)
    val resp = createKFV1ModelReadyResponse(modelManager, modelName, model)
    NettyUtils.sendJsonResponse(ctx, resp)
  }

  private def createKFV1ModelReadyResponse(modelManager: ModelManager, modelName: String, model: Model) = {
    val resp = new KFV1ModelReadyResponse
    val workers = modelManager.getWorkers(model.getModelVersionName)
    resp.setName(modelName)
    resp.setReady(!workers.isEmpty)
    resp
  }

  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkerInitializationException]
  private def handleRegisterModel(ctx: ChannelHandlerContext, decoder: QueryStringDecoder, req: FullHttpRequest): Unit = {
    val registerModelRequest = parseRequest(req, decoder)
    var statusResponse: StatusResponse = null
    try statusResponse = ApiUtils.registerModel(registerModelRequest)
    catch {
      case e@(_: ExecutionException | _: InterruptedException | _: InternalServerException) =>
        var message: String = null
        if (e.isInstanceOf[InternalServerException]) message = e.getMessage
        else message = "Error while creating workers"
        statusResponse = new StatusResponse
        statusResponse.setE(e)
        statusResponse.setStatus(message)
        statusResponse.setHttpResponseCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code)
    }
    sendResponse(ctx, statusResponse)
  }

  @throws[ModelNotFoundException]
  @throws[InternalServerException]
  @throws[RequestTimeoutException]
  @throws[ModelVersionNotFoundException]
  private def handleUnregisterModel(ctx: ChannelHandlerContext, modelName: String, modelVersion: String): Unit = {
    ApiUtils.unregisterModel(modelName, modelVersion)
    val msg = "Model \"" + modelName + "\" unregistered"
    NettyUtils.sendJsonResponse(ctx, new StatusResponse(msg, HttpResponseStatus.OK.code))
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  @throws[WorkerInitializationException]
  private def handleScaleModel(ctx: ChannelHandlerContext, decoder: QueryStringDecoder, modelName: String, modelVersionTmp: String): Unit = {
    val minWorkers = NettyUtils.getIntParameter(decoder, "min_worker", 1)
    val maxWorkers = NettyUtils.getIntParameter(decoder, "max_worker", minWorkers)
    val modelVersion = if (modelVersionTmp == null) then  NettyUtils.getParameter(decoder, "model_version", null) else modelVersionTmp
    val synchronous = NettyUtils.getParameter(decoder, "synchronous", null).asInstanceOf[Boolean]
    var statusResponse: StatusResponse = null
    try statusResponse = ApiUtils.updateModelWorkers(modelName, modelVersion, minWorkers, maxWorkers, synchronous, false, null)
    catch {
      case e@(_: ExecutionException | _: InterruptedException) =>
        statusResponse = new StatusResponse
        statusResponse.setE(e)
        statusResponse.setStatus("Error while creating workers")
        statusResponse.setHttpResponseCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code)
    }
    sendResponse(ctx, statusResponse)
  }

  private def parseRequest(req: FullHttpRequest, decoder: QueryStringDecoder) = {
    var in: RegisterModelRequest = null
    val mime = HttpUtil.getMimeType(req)
    if (HttpHeaderValues.APPLICATION_JSON.contentEqualsIgnoreCase(mime)) in = JsonUtils.GSON.fromJson(req.content.toString(CharsetUtil.UTF_8), classOf[RegisterModelRequest])
    else in = new RegisterModelRequest(decoder)
    in
  }

  private def setDefaultModelVersion(ctx: ChannelHandlerContext, modelName: String, newModelVersion: String): Unit = {
    try {
      val msg = ApiUtils.setDefault(modelName, newModelVersion)
      NettyUtils.sendJsonResponse(ctx, new StatusResponse(msg, HttpResponseStatus.OK.code))
    } catch {
      case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
        NettyUtils.sendError(ctx, HttpResponseStatus.NOT_FOUND, e)
    }
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
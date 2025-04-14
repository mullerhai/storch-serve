package org.pytorch.serve.http.api.rest

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.multipart.HttpDataFactory
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import java.net.HttpURLConnection
import java.util
import java.util.UUID
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.archive.model.ModelVersionNotFoundException
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.http.BadRequestException
import org.pytorch.serve.http.HttpRequestHandlerChain
import org.pytorch.serve.http.ResourceNotFoundException
import org.pytorch.serve.http.StatusResponse
import org.pytorch.serve.metrics.IMetric
import org.pytorch.serve.metrics.MetricCache
import org.pytorch.serve.openapi.OpenApiUtils
import org.pytorch.serve.servingsdk.ModelServerEndpoint
import org.pytorch.serve.util.ApiUtils
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.util.messages.InputParameter
import org.pytorch.serve.util.messages.RequestInput
import org.pytorch.serve.wlm.Model
import org.pytorch.serve.wlm.ModelManager
import org.pytorch.serve.wlm.WorkerInitializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
/**
 * A class handling inbound HTTP requests to the inference API.
 *
 * <p>This class
 */
object InferenceRequestHandler {
  private val logger = LoggerFactory.getLogger(classOf[InferenceRequestHandler])

  private def parseRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder) = {
    val requestId = NettyUtils.getRequestId(ctx.channel)
    val inputData = new RequestInput(requestId)
    if (decoder != null) {
//      import scala.collection.JavaConversions._
      for (entry <- decoder.parameters.entrySet.asScala) {
        val key = entry.getKey
//        import scala.collection.JavaConversions._
        for (value <- entry.getValue.asScala) {
          inputData.addParameter(new InputParameter(key, value))
        }
      }
    }
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

class InferenceRequestHandler(ep: util.Map[String, ModelServerEndpoint])

/** Creates a new {@code InferenceRequestHandler} instance. */
  extends HttpRequestHandlerChain {
  endpointMap = ep

  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    if (isInferenceReq(segments)) if (endpointMap.getOrDefault(segments(1), null) != null) handleCustomEndpoint(ctx, req, segments, decoder)
    else segments(1) match {
      case "ping" =>
        val r: Runnable= () => {
          val isHealthy = ApiUtils.isModelHealthy
          var code = HttpURLConnection.HTTP_OK
          var response = "Healthy"
          if (!isHealthy) {
            response = "Unhealthy"
            code = HttpURLConnection.HTTP_INTERNAL_ERROR
          }
          NettyUtils.sendJsonResponse(ctx, new StatusResponse(response, code))
        }
        ApiUtils.getTorchServeHealth(r)
      case "models" =>
      case "invocations" =>
        validatePredictionsEndpoint(segments)
        handleInvocations(ctx, req, decoder, segments)
      case "predictions" =>
        handlePredictions(ctx, req, segments, false)
      case "explanations" =>
        handlePredictions(ctx, req, segments, true)
      case _ =>
        handleLegacyPredict(ctx, req, decoder, segments)
    }
    else if (isKFV1InferenceReq(segments)) if (segments(3).contains(":predict")) handleKFV1Predictions(ctx, req, segments, false)
    else if (segments(3).contains(":explain")) handleKFV1Predictions(ctx, req, segments, true)
    else if (isKFV2InferenceReq(segments)) if (segments(4) == "infer") handleKFV2Predictions(ctx, req, segments, false)
    else if (segments(4) == "explain") handleKFV2Predictions(ctx, req, segments, true)
    else chain.handleRequest(ctx, req, decoder, segments)
  }

  private def isInferenceReq(segments: Array[String]) = segments.length == 0 || (segments.length >= 2 && (segments(1) == "ping" || segments(1) == "predictions" || segments(1) == "explanations" || segments(1) == "api-description" || segments(1) == "invocations" || endpointMap.containsKey(segments(1)))) || (segments.length == 4 && segments(1) == "models") || (segments.length == 3 && segments(2) == "predict") || (segments.length == 4 && segments(3) == "predict")

  private def isKFV1InferenceReq(segments: Array[String]) = segments.length == 4 && "v1" == segments(1) && "models" == segments(2) && (segments(3).contains(":predict") || segments(3).contains(":explain"))

  private def isKFV2InferenceReq(segments: Array[String]) = segments.length == 5 && "v2" == segments(1) && "models" == segments(2) && (segments(4) == "infer" || segments(4) == "explain")

  private def validatePredictionsEndpoint(segments: Array[String]): Unit = {
    if (segments.length == 2 && "invocations" == segments(1)) return
    else if (segments.length == 4 && "models" == segments(1) && "invoke" == segments(3)) return
    throw new ResourceNotFoundException
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  private def handlePredictions(ctx: ChannelHandlerContext, req: FullHttpRequest, segments: Array[String], explain: Boolean): Unit = {
    if (segments.length < 3) throw new ResourceNotFoundException
    var modelVersion: String = null
    if (segments.length >= 4) modelVersion = segments(3)
    req.headers.add("url_path", "")

    /**
     * If url provides more segments as model_name/version we provide these as url_path in the
     * request header This way users can leverage them in the custom handler to e.g. influence
     * handler behavior
     */
    if (segments.length > 4) {
      val segmentArray = util.Arrays.copyOfRange(segments, 4, segments.length)
     
      val joinedSegments =  Seq(segmentArray).mkString("/")
      req.headers.add("url_path", joinedSegments)
    }
    req.headers.add("explain", "False")
    if (explain) req.headers.add("explain", "True")
    predict(ctx, req, null, segments(2), modelVersion)
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  private def handleKFV1Predictions(ctx: ChannelHandlerContext, req: FullHttpRequest, segments: Array[String], explain: Boolean): Unit = {
    val modelVersion: String = null
    val modelName = segments(3).split(":")(0)
    req.headers.add("explain", "False")
    if (explain) req.headers.add("explain", "True")
    predict(ctx, req, null, modelName, modelVersion)
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  private def handleKFV2Predictions(ctx: ChannelHandlerContext, req: FullHttpRequest, segments: Array[String], explain: Boolean): Unit = {
    val modelVersion: String = null
    val modelName = segments(3).split(":")(0)
    req.headers.add("explain", "False")
    if (explain) req.headers.add("explain", "True")
    predict(ctx, req, null, modelName, modelVersion)
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  private def handleInvocations(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    var modelName = if ("invocations" == (segments(1))) NettyUtils.getParameter(decoder, "model_name", null)
    else segments(2)
    if (modelName == null || modelName.isEmpty) if (ModelManager.getInstance.getStartupModels.size == 1) modelName = ModelManager.getInstance.getStartupModels.iterator.next
    predict(ctx, req, decoder, modelName, null)
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  private def handleLegacyPredict(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    var modelVersion: String = null
    if (segments.length == 4 && "predict" == segments(3)) modelVersion = segments(2)
    else if (segments.length < 3 || !("predict" == segments(2))) throw new ResourceNotFoundException
    predict(ctx, req, decoder, segments(1), modelVersion)
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  private def predict(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, modelNameTmp: String, modelVersion: String): Unit = {
    val input = InferenceRequestHandler.parseRequest(ctx, req, decoder)
    var modelName: String = null
    if (modelNameTmp == null) {
      modelName = input.getStringParameter("model_name")
      if (modelNameTmp == null) throw new BadRequestException("Parameter model_name is required.")
    }
    val modelManager = ModelManager.getInstance
    val model = modelManager.getModel(modelName, modelVersion)
    if (model == null) throw new ModelNotFoundException("Model not found: " + modelName)
    input.setClientExpireTS(model.getClientTimeoutInMills)
    if (model.isSequenceBatching) {
      var sequenceId = input.getSequenceId
      if ("" == sequenceId) {
        sequenceId = String.format("ts-seq-%s", UUID.randomUUID)
        input.updateHeaders(ConfigManager.getInstance.getTsHeaderKeySequenceStart, "true")
      }
      input.updateHeaders(ConfigManager.getInstance.getTsHeaderKeySequenceId, sequenceId)
    }
    if (HttpMethod.OPTIONS == req.method) {
      val resp = OpenApiUtils.getModelApi(model)
      NettyUtils.sendJsonResponse(ctx, resp)
      return
    }
    val inferenceRequestsTotalMetric = MetricCache.getInstance.getMetricFrontend("ts_inference_requests_total")
    if (inferenceRequestsTotalMetric != null) {
      val inferenceRequestsTotalMetricDimensionValues = util.Arrays.asList(modelName, if (modelVersion == null) "default"
      else modelVersion, ConfigManager.getInstance.getHostName)
      try inferenceRequestsTotalMetric.addOrUpdate(inferenceRequestsTotalMetricDimensionValues, 1)
      catch {
        case e: Exception =>
          InferenceRequestHandler.logger.error("Failed to update frontend metric ts_inference_requests_total: ", e)
      }
    }
    ApiUtils.addRESTInferenceJob(ctx, modelName, modelVersion, input)
  }
}
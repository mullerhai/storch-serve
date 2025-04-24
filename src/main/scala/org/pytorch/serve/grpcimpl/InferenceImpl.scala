package org.pytorch.serve.grpcimpl

import com.google.protobuf.{Any, ByteString, Empty, empty}
import com.google.rpc.ErrorInfo
import io.grpc.stub.{ServerCallStreamObserver, StreamObserver}
import io.grpc.{BindableService, ServerServiceDefinition, Status}
import org.pytorch.serve.archive.model.{ModelNotFoundException, ModelVersionNotFoundException}
import org.pytorch.serve.grpc.inference.inference.InferenceAPIsServiceGrpc.InferenceAPIsService
import org.pytorch.serve.grpc.inference.inference.{InferenceAPIsServiceGrpc, PredictionResponse, PredictionsRequest, TorchServeHealthResponse}
import org.pytorch.serve.http.{BadRequestException, InternalServerException, StatusResponse}
import org.pytorch.serve.job.{GRPCJob, JobGroup}
import org.pytorch.serve.metrics.MetricCache
import org.pytorch.serve.util.messages.{InputParameter, RequestInput, WorkerCommands}
import org.pytorch.serve.util.{ApiUtils, ConfigManager, JsonUtils}
import org.pytorch.serve.wlm.ModelManager
import org.slf4j.LoggerFactory

import java.net.HttpURLConnection
import java.util
import java.util.UUID
import scala.jdk.CollectionConverters.*
object InferenceImpl {
  private val logger = LoggerFactory.getLogger(classOf[InferenceImpl])
  private val strFalse = ByteString.copyFromUtf8("false")
}

class InferenceImpl extends InferenceAPIsService with BindableService {
   def ping(request: Empty, responseObserver: StreamObserver[TorchServeHealthResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[TorchServeHealthResponse]].setOnCancelHandler(() => {
      InferenceImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val r:Runnable = () => {
      val isHealthy = ApiUtils.isModelHealthy
      var code = HttpURLConnection.HTTP_OK
      var response = "Healthy"
      if (!isHealthy) {
        response = "Unhealthy"
        code = HttpURLConnection.HTTP_INTERNAL_ERROR
      }
      val reply = TorchServeHealthResponse.defaultInstance
      reply.withHealth(JsonUtils.GSON_PRETTY_EXPOSED.toJson(new StatusResponse(response, code)))
      responseObserver.onNext(reply)
      responseObserver.onCompleted()
    }
    ApiUtils.getTorchServeHealth(r)
  }

   def predictions(request: PredictionsRequest, responseObserver: StreamObserver[PredictionResponse]): Unit = {
    prediction(request, responseObserver, WorkerCommands.PREDICT)
  }

   def streamPredictions(request: PredictionsRequest, responseObserver: StreamObserver[PredictionResponse]): Unit = {
    prediction(request, responseObserver, WorkerCommands.STREAMPREDICT)
  }

   def streamPredictions2(responseObserver: StreamObserver[PredictionResponse]): StreamObserver[PredictionsRequest] = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[PredictionResponse]].setOnCancelHandler(() => {
      InferenceImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    new StreamObserver[PredictionsRequest]() {
      private var jobGroup: JobGroup = null

      override def onNext(value: PredictionsRequest): Unit = {
        val not_has_seq_id = "" == value.getSequenceId
        val start = ConfigManager.getInstance.getTsHeaderKeySequenceStart
        val strFalse = InferenceImpl.strFalse
        val has_seq_in_header = !value.input.getOrElse(start,strFalse).toString.asInstanceOf[Boolean]
//        val has_seq_in_header = !value.getInputOrDefault(ConfigManager.getInstance.getTsHeaderKeySequenceStart, InferenceImpl.strFalse).toString.asInstanceOf[Boolean]
        if (not_has_seq_id && has_seq_in_header) {
          val e = new BadRequestException("Parameter sequenceId is required.")
          sendErrorResponse(responseObserver, Status.INTERNAL, e, "BadRequestException.()", WorkerCommands.STREAMPREDICT2)
        }
        else {
          prediction(value, responseObserver, WorkerCommands.STREAMPREDICT2)
          if (jobGroup == null) jobGroup = getJobGroup(value)
        }
      }

      override def onError(t: Throwable): Unit = {
        InferenceImpl.logger.error("Failed to process the streaming requestId: {} in sequenceId: {}", if (jobGroup == null) null
        else jobGroup.getGroupId, t)
      }

      override def onCompleted(): Unit = {
        if (jobGroup != null) InferenceImpl.logger.info("SequenceId {} is completed", jobGroup.getGroupId)
        responseObserver.onCompleted()
      }
    }
  }

  private def sendErrorResponse(responseObserver: StreamObserver[PredictionResponse], status: Status, e: Exception, description: String, workerCmd: WorkerCommands): Unit = {
    if (workerCmd eq WorkerCommands.STREAMPREDICT2) {
      val rpcStatus = com.google.rpc.Status.newBuilder.setCode(status.getCode.value).setMessage(e.getMessage).addDetails(Any.pack(ErrorInfo.newBuilder.setReason(if (description == null) e.getClass.getCanonicalName
      else description).build)).build
      val response = PredictionResponse.defaultInstance 
      response.withStatus(null)//rpcStatus) //// todo scalapb generate bug
      responseObserver.onNext(response)
    }
    else responseObserver.onError(status.withDescription(e.getMessage).augmentDescription(if (description == null) e.getClass.getCanonicalName
    else description).withCause(e).asRuntimeException)
  }

  private def prediction(request: PredictionsRequest, responseObserver: StreamObserver[PredictionResponse], workerCmd: WorkerCommands): Unit = {
    if (workerCmd ne WorkerCommands.STREAMPREDICT2) responseObserver.asInstanceOf[ServerCallStreamObserver[PredictionResponse]].setOnCancelHandler(() => {
      InferenceImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val modelName = request.modelName
    var modelVersion = request.modelVersion
    if ("" == modelName) {
      val e = new BadRequestException("Parameter model_name is required.")
      sendErrorResponse(responseObserver, Status.INTERNAL, e, "BadRequestException.()", workerCmd)
      return
    }
    if ("" == modelVersion) modelVersion = null
    val requestId = UUID.randomUUID.toString
    val inputData = new RequestInput(requestId)
    try {
      val modelManager = ModelManager.getInstance
      val model = modelManager.getModel(modelName, modelVersion)
      if (model == null) throw new ModelNotFoundException("Model not found: " + modelName)
      inputData.setClientExpireTS(model.getClientTimeoutInMills)
//      import scala.collection.JavaConversions._
      for (entry <- request.input.iterator) {
        inputData.addParameter(new InputParameter(entry._1, entry._2.toByteArray))
      }
      if (workerCmd eq WorkerCommands.STREAMPREDICT2) {
        var sequenceId = request.getSequenceId
        if ("" == sequenceId) {
          sequenceId = String.format("ts-seq-%s", UUID.randomUUID)
          inputData.updateHeaders(ConfigManager.getInstance.getTsHeaderKeySequenceStart, "true")
        }
        inputData.updateHeaders(ConfigManager.getInstance.getTsHeaderKeySequenceId, sequenceId)
        if (!java.lang.Boolean.parseBoolean(request.input.getOrElse(ConfigManager.getInstance.getTsHeaderKeySequenceEnd, InferenceImpl.strFalse).toString.toLowerCase)) inputData.updateHeaders(ConfigManager.getInstance.getTsHeaderKeySequenceEnd, "true")
      }
      val inferenceRequestsTotalMetric = MetricCache.getInstance.getMetricFrontend("ts_inference_requests_total")
      if (inferenceRequestsTotalMetric != null) {
        val inferenceRequestsTotalMetricDimensionValues = util.Arrays.asList(modelName, if (modelVersion == null) "default"
        else modelVersion, ConfigManager.getInstance.getHostName)
        try inferenceRequestsTotalMetric.addOrUpdate(inferenceRequestsTotalMetricDimensionValues.asScala.toList, 1)
        catch {
          case e: Exception =>
            InferenceImpl.logger.error("Failed to update frontend metric ts_inference_requests_total: ", e)
        }
      }
      val job = new GRPCJob(responseObserver, modelName, modelVersion, workerCmd, inputData)
      if (!modelManager.addJob(job)) {
        val e = new InternalServerException(ApiUtils.getStreamingInferenceErrorResponseMessage(modelName, modelVersion))
        sendErrorResponse(responseObserver, Status.INTERNAL, e, "InternalServerException.()", workerCmd)
      }
    } catch {
      case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
        sendErrorResponse(responseObserver, Status.INTERNAL, e, null, workerCmd)
    }
  }

  private def getJobGroup(request: PredictionsRequest): JobGroup = {
    try {
      val modelName = request.modelName
      var modelVersion = request.modelVersion
      if ("" == modelVersion) modelVersion = null
      val modelManager = ModelManager.getInstance
      val model = modelManager.getModel(modelName, modelVersion)
      return model.getJobGroup(request.getSequenceId)
    } catch {
      case e: ModelVersionNotFoundException =>
        InferenceImpl.logger.error("Failed to get jobGroup", e)
    }
    null
  }

  /** Check health status of the TorchServe server.
   */
  override def ping(request: empty.Empty) = ???

  /** Predictions entry point to get inference using default model version.
   */
  override def predictions(request: PredictionsRequest) = ???

  override def bindService(): ServerServiceDefinition = ???
}
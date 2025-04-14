package org.pytorch.serve.grpcimpl

import io.grpc.{BindableService, ServerServiceDefinition, Status}
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver

import java.util.UUID
import java.util.concurrent.ExecutionException
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.archive.model.ModelVersionNotFoundException
import org.pytorch.serve.grpc.management.management.ManagementAPIsServiceGrpc.ManagementAPIsService
import org.pytorch.serve.grpc.management.management.{DescribeModelRequest, ListModelsRequest, ManagementAPIsServiceGrpc, ManagementResponse, RegisterModelRequest, ScaleWorkerRequest, SetDefaultRequest, UnregisterModelRequest}
//import org.pytorch.serve.grpc.management.management.ManagementAPIsServiceGrpc.ManagementAPIsServiceImplBase
import org.pytorch.serve.http.BadRequestException
import org.pytorch.serve.http.InternalServerException
import org.pytorch.serve.http.StatusResponse
import org.pytorch.serve.job.GRPCJob
import org.pytorch.serve.job.Job
import org.pytorch.serve.util.ApiUtils
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.GRPCUtils
import org.pytorch.serve.util.JsonUtils
import org.pytorch.serve.util.messages.RequestInput
import org.pytorch.serve.wlm.ModelManager
import org.pytorch.serve.wlm.WorkerInitializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.pytorch.serve.http.messages.RegisterModelRequest as RegRequest

object ManagementImpl {
  private val logger = LoggerFactory.getLogger(classOf[ManagementImpl])

  def sendErrorResponse(responseObserver: StreamObserver[ManagementResponse], status: Status, e: Exception): Unit = {
    responseObserver.onError(status.withDescription(e.getMessage).augmentDescription(e.getClass.getCanonicalName).asRuntimeException)
  }
}

class ManagementImpl extends ManagementAPIsService with BindableService {

  private var configManager: ConfigManager = ConfigManager.getInstance

  def describeModel(request: DescribeModelRequest, responseObserver: StreamObserver[ManagementResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ManagementResponse]].setOnCancelHandler(() => {
      ManagementImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val requestId = UUID.randomUUID.toString
    val input = new RequestInput(requestId)
    val modelName = request.modelName
    var modelVersion: String = null
    if (!request.modelVersion.isEmpty) modelVersion = request.modelVersion
    val customized = request.customized
    if ("all" == modelVersion || !customized) {
      var resp: String = null
      try {
        resp = JsonUtils.GSON_PRETTY.toJson(ApiUtils.getModelDescription(modelName, modelVersion))
        sendResponse(responseObserver, resp)
      } catch {
        case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
          ManagementImpl.sendErrorResponse(responseObserver, Status.NOT_FOUND, e)
      }
    }
    else {
      input.updateHeaders("describe", "True")
      val job = new GRPCJob(responseObserver, modelName, modelVersion, input)
      try if (!ModelManager.getInstance.addJob(job)) {
        val responseMessage = ApiUtils.getDescribeErrorResponseMessage(modelName)
        val e = new InternalServerException(responseMessage)
        sendException(responseObserver, e, "InternalServerException.()")
      }
      catch {
        case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
          ManagementImpl.sendErrorResponse(responseObserver, Status.INTERNAL, e)
      }
    }
  }

  def listModels(request: ListModelsRequest, responseObserver: StreamObserver[ManagementResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ManagementResponse]].setOnCancelHandler(() => {
      ManagementImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val limit = request.limit
    val pageToken = request.nextPageToken
    val modelList = JsonUtils.GSON_PRETTY.toJson(ApiUtils.getModelList(limit, pageToken))
    sendResponse(responseObserver, modelList)
  }

  def registerModel(request: RegisterModelRequest, responseObserver: StreamObserver[ManagementResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ManagementResponse]].setOnCancelHandler(() => {
      ManagementImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val registerModelRequest = new RegRequest(request)
    var statusResponse: StatusResponse = null
    try {
      if (!configManager.isModelApiEnabled) {
        ManagementImpl.sendErrorResponse(responseObserver, Status.PERMISSION_DENIED, new ModelException("Model API disabled"))
        return
      }
      statusResponse = ApiUtils.registerModel(registerModelRequest)
      sendStatusResponse(responseObserver, statusResponse)
    } catch {
      case e: InternalServerException =>
        sendException(responseObserver, e, null)
      case e@(_: ExecutionException | _: InterruptedException | _: WorkerInitializationException) =>
        sendException(responseObserver, e, "Error while creating workers")
      case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
        ManagementImpl.sendErrorResponse(responseObserver, Status.NOT_FOUND, e)
      case e@(_: ModelException | _: BadRequestException | _: DownloadArchiveException) =>
        ManagementImpl.sendErrorResponse(responseObserver, Status.INVALID_ARGUMENT, e)
    }
  }

  def scaleWorker(request: ScaleWorkerRequest, responseObserver: StreamObserver[ManagementResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ManagementResponse]].setOnCancelHandler(() => {
      ManagementImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val minWorkers = GRPCUtils.getRegisterParam(request.minWorker, 1)
    val maxWorkers = GRPCUtils.getRegisterParam(request.maxWorker, minWorkers)
    val modelName = GRPCUtils.getRegisterParam(request.modelName, null)
    val modelVersion = GRPCUtils.getRegisterParam(request.modelVersion, null)
    val synchronous = request.synchronous
    var statusResponse: StatusResponse = null
    try {
      statusResponse = ApiUtils.updateModelWorkers(modelName, modelVersion, minWorkers, maxWorkers, synchronous, false, null)
      sendStatusResponse(responseObserver, statusResponse)
    } catch {
      case e@(_: ExecutionException | _: InterruptedException | _: WorkerInitializationException) =>
        sendException(responseObserver, e, "Error while creating workers")
      case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
        ManagementImpl.sendErrorResponse(responseObserver, Status.NOT_FOUND, e)
      case e: BadRequestException =>
        ManagementImpl.sendErrorResponse(responseObserver, Status.INVALID_ARGUMENT, e)
    }
  }

  def setDefault(request: SetDefaultRequest, responseObserver: StreamObserver[ManagementResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ManagementResponse]].setOnCancelHandler(() => {
      ManagementImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val modelName = request.modelName
    val newModelVersion = request.modelVersion
    try {
      val msg = ApiUtils.setDefault(modelName, newModelVersion)
      sendResponse(responseObserver, msg)
    } catch {
      case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
        ManagementImpl.sendErrorResponse(responseObserver, Status.NOT_FOUND, e)
    }
  }

  def unregisterModel(request: UnregisterModelRequest, responseObserver: StreamObserver[ManagementResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ManagementResponse]].setOnCancelHandler(() => {
      ManagementImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    try {
      if (!configManager.isModelApiEnabled) {
        ManagementImpl.sendErrorResponse(responseObserver, Status.PERMISSION_DENIED, new ModelException("Model API disabled"))
        return
      }
      val modelName = request.modelName
      if (modelName == null || "" == modelName) ManagementImpl.sendErrorResponse(responseObserver, Status.INVALID_ARGUMENT, new BadRequestException("Parameter url is required."))
      var modelVersion = request.modelVersion
      if ("" == modelVersion) modelVersion = null
      ApiUtils.unregisterModel(modelName, modelVersion)
      val msg = "Model \"" + modelName + "\" unregistered"
      sendResponse(responseObserver, msg)
    } catch {
      case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
        ManagementImpl.sendErrorResponse(responseObserver, Status.NOT_FOUND, e)
      case e: BadRequestException =>
        ManagementImpl.sendErrorResponse(responseObserver, Status.INVALID_ARGUMENT, e)
    }
  }

  private def sendResponse(responseObserver: StreamObserver[ManagementResponse], msg: String): Unit = {
    val reply = ManagementResponse.of(msg) //.newBuilder.setMsg(msg).build
    responseObserver.onNext(reply)
    responseObserver.onCompleted()
  }

  private def sendErrorResponse(responseObserver: StreamObserver[ManagementResponse], status: Status, description: String, errorClass: String): Unit = {
    responseObserver.onError(status.withDescription(description).augmentDescription(errorClass).asRuntimeException)
  }

  private def sendStatusResponse(responseObserver: StreamObserver[ManagementResponse], statusResponse: StatusResponse): Unit = {
    val httpResponseStatusCode = statusResponse.getHttpResponseCode
    if (httpResponseStatusCode >= 200 && httpResponseStatusCode < 300) sendResponse(responseObserver, statusResponse.getStatus)
    else sendErrorResponse(responseObserver, GRPCUtils.getGRPCStatusCode(statusResponse.getHttpResponseCode), statusResponse.getE.getMessage, statusResponse.getE.getClass.getCanonicalName)
  }

  private def sendException(responseObserver: StreamObserver[ManagementResponse], e: Exception, description: String): Unit = {
    sendErrorResponse(responseObserver, Status.INTERNAL, if (description == null) e.getMessage
    else description, e.getClass.getCanonicalName)
  }

  /** Provides detailed information about the default version of a model.
   */
  override def describeModel(request: DescribeModelRequest) = ???

  /** List registered models in TorchServe.
   */
  override def listModels(request: ListModelsRequest) = ???

  /** Register a new model in TorchServe.
   */
  override def registerModel(request: RegisterModelRequest) = ???

  /** Configure number of workers for a default version of a model. This is an asynchronous call by default. Caller need to call describeModel to check if the model workers has been changed.
   */
  override def scaleWorker(request: ScaleWorkerRequest) = ???

  /** Set default version of a model
   */
  override def setDefault(request: SetDefaultRequest) = ???

  /** Unregister the default version of a model from TorchServe if it is the only version available. This is an asynchronous call by default. Caller can call listModels to confirm model is unregistered.
   */
  override def unregisterModel(request: UnregisterModelRequest) = ???

  override def bindService(): ServerServiceDefinition = ???
}
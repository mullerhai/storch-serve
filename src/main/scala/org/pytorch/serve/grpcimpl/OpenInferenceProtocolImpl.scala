package org.pytorch.serve.grpcimpl

import com.google.gson.Gson
import com.google.protobuf.ByteString
import io.grpc.{BindableService, ServerServiceDefinition, Status}
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.StreamObserver

import java.nio.charset.StandardCharsets
import java.util
import java.util.Base64
import java.util.UUID
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.archive.model.ModelVersionNotFoundException
import org.pytorch.serve.grpc.openinference.open_inference_grpc.GRPCInferenceServiceGrpc.GRPCInferenceService
import org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.GRPCInferenceServiceGrpc.GRPCInferenceServiceImplBase
import org.pytorch.serve.grpc.openinference.open_inference_grpc.{ModelInferRequest, ModelInferResponse, ModelMetadataRequest, ModelMetadataResponse, ModelReadyRequest, ModelReadyResponse, ServerLiveRequest, ServerLiveResponse, ServerReadyRequest, ServerReadyResponse}
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ModelInferRequest
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ModelInferRequest.InferInputTensor
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ModelInferResponse
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ModelMetadataRequest
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ModelMetadataResponse
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ModelMetadataResponse.TensorMetadata
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ModelReadyRequest
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ModelReadyResponse
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ServerLiveRequest
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ServerLiveResponse
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ServerReadyRequest
//import org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpc.ServerReadyResponse
import org.pytorch.serve.grpc.openinference.open_inference_grpc.GRPCInferenceServiceGrpc
import org.pytorch.serve.http.BadRequestException
import org.pytorch.serve.http.InternalServerException
import org.pytorch.serve.job.GRPCJob
import org.pytorch.serve.job.Job
import org.pytorch.serve.util.ApiUtils
import org.pytorch.serve.util.messages.InputParameter
import org.pytorch.serve.util.messages.RequestInput
import org.pytorch.serve.util.messages.WorkerCommands
import org.pytorch.serve.wlm.Model
import org.pytorch.serve.wlm.ModelManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*
object OpenInferenceProtocolImpl {
  private val logger = LoggerFactory.getLogger(classOf[OpenInferenceProtocolImpl])

  private def setInputContents(inferInputTensor: ModelInferRequest.InferInputTensor, inferInputMap: util.Map[String, AnyRef]): Unit = {
    inferInputTensor.datatype match {
      case "BYTES" =>
        val byteStrings = inferInputTensor.getContents.bytesContents //.getBytesContentsList
        val base64Strings = new util.ArrayList[String]
       
        for (byteString <- byteStrings) {
          val base64String = Base64.getEncoder.encodeToString(byteString.toByteArray)
          base64Strings.add(base64String)
        }
        inferInputMap.put("data", base64Strings)
      case "FP32" =>
        val fp32Contents = inferInputTensor.getContents.fp32Contents //.getFp32ContentsList
        inferInputMap.put("data", fp32Contents)
      case "FP64" =>
        val fp64ContentList = inferInputTensor.getContents.fp64Contents //.getFp64ContentsList
        inferInputMap.put("data", fp64ContentList)
      case "INT8" => // jump to INT32 case
      case "INT16" => // jump to INT32 case
      case "INT32" =>
        val int32Contents = inferInputTensor.getContents.intContents //.getIntContentsList
        inferInputMap.put("data", int32Contents)
      case "INT64" =>
        val int64Contents = inferInputTensor.getContents.int64Contents //.getInt64ContentsList
        inferInputMap.put("data", int64Contents)
      case "UINT8" => // jump to UINT32 case
      case "UINT16" => // jump to UINT32 case
      case "UINT32" =>
        val uint32Contents = inferInputTensor.getContents.uintContents //.getUintContentsList
        inferInputMap.put("data", uint32Contents)
      case "UINT64" =>
        val uint64Contents = inferInputTensor.getContents.uint64Contents //.getUint64ContentsList
        inferInputMap.put("data", uint64Contents)
      case "BOOL" =>
        val boolContents = inferInputTensor.getContents.boolContents //.getBoolContentsList
        inferInputMap.put("data", boolContents)
      case _ =>
    }
  }
}

class OpenInferenceProtocolImpl extends GRPCInferenceService with BindableService {
  def serverLive(request: ServerLiveRequest, responseObserver: StreamObserver[ServerLiveResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ServerLiveResponse]].setOnCancelHandler(() => {
      OpenInferenceProtocolImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val readyResponse = ServerLiveResponse.of(true) //.newBuilder.setLive(true).build
    responseObserver.onNext(readyResponse)
    responseObserver.onCompleted()
  }

  def serverReady(request: ServerReadyRequest, responseObserver: StreamObserver[ServerReadyResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ServerReadyResponse]].setOnCancelHandler(() => {
      OpenInferenceProtocolImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val readyResponse = ServerReadyResponse.of(true) //.newBuilder.setReady(true).build
    responseObserver.onNext(readyResponse)
    responseObserver.onCompleted()
  }

  private def sendErrorResponse(responseObserver: StreamObserver[_], internal: Status, e: Exception, string: String): Unit = {
    responseObserver.onError(internal.withDescription(e.getMessage).augmentDescription(if (string == null) e.getClass.getCanonicalName
    else string).withCause(e).asRuntimeException)
  }

  def modelReady(request: ModelReadyRequest, responseObserver: StreamObserver[ModelReadyResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ModelReadyResponse]].setOnCancelHandler(() => {
      OpenInferenceProtocolImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val modelName = request.name
    var modelVersion = request.version
    val modelManager = ModelManager.getInstance
    var isModelReady = false
    if (modelName == null || "" == modelName) {
      val e = new BadRequestException("Parameter name is required.")
      sendErrorResponse(responseObserver, Status.INTERNAL, e, "BadRequestException.()")
      return
    }
    if (modelVersion == null || "" == modelVersion) modelVersion = null
    try {
      val model = modelManager.getModel(modelName, modelVersion)
      if (model == null) throw new ModelNotFoundException("Model not found: " + modelName)
      val numScaled = model.getMinWorkers
      val numHealthy = modelManager.getNumHealthyWorkers(model.getModelVersionName)
      isModelReady = numHealthy >= numScaled
      val modelReadyResponse = ModelReadyResponse.of(isModelReady) //.newBuilder.setReady(isModelReady).build
      responseObserver.onNext(modelReadyResponse)
      responseObserver.onCompleted()
    } catch {
      case e@(_: ModelVersionNotFoundException | _: ModelNotFoundException) =>
        sendErrorResponse(responseObserver, Status.NOT_FOUND, e, null)
    }
  }

  def modelMetadata(request: ModelMetadataRequest, responseObserver: StreamObserver[ModelMetadataResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ModelMetadataResponse]].setOnCancelHandler(() => {
      OpenInferenceProtocolImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val modelName = request.name
    var modelVersion = request.version
    val modelManager = ModelManager.getInstance
    val response = ModelMetadataResponse.defaultInstance //.newBuilder
    val inputs = new util.ArrayList[ModelMetadataResponse.TensorMetadata]
    val outputs = new util.ArrayList[ModelMetadataResponse.TensorMetadata]
    val versions = new util.ArrayList[String]
    if (modelName == null || "" == modelName) {
      val e = new BadRequestException("Parameter model_name is required.")
      sendErrorResponse(responseObserver, Status.INTERNAL, e, "BadRequestException.()")
      return
    }
    if (modelVersion == null || "" == modelVersion) modelVersion = null
    try {
      val model = modelManager.getModel(modelName, modelVersion)
      if (model == null) throw new ModelNotFoundException("Model not found: " + modelName)
      modelManager.getAllModelVersions(modelName).forEach((entry: util.Map.Entry[String, Model]) => versions.add(entry.getKey))
      response.withName(modelName)
      response.addAllVersions(versions.asScala)
      response.withPlatform("")
      response.addAllInputs(inputs.asScala)
      response.addAllOutputs(outputs.asScala)
      responseObserver.onNext(response)
      responseObserver.onCompleted()
    } catch {
      case e@(_: ModelVersionNotFoundException | _: ModelNotFoundException) =>
        sendErrorResponse(responseObserver, Status.NOT_FOUND, e, null)
    }
  }

  def modelInfer(request: ModelInferRequest, responseObserver: StreamObserver[ModelInferResponse]): Unit = {
    responseObserver.asInstanceOf[ServerCallStreamObserver[ModelInferResponse]].setOnCancelHandler(() => {
      OpenInferenceProtocolImpl.logger.warn("grpc client call already cancelled")
      responseObserver.onError(io.grpc.Status.CANCELLED.withDescription("call already cancelled").asRuntimeException)
    })
    val modelName = request.modelName
    var modelVersion = request.modelVersion
    val contentsType = "application/json"
    val gson = new Gson
    val modelInferMap = new util.HashMap[String, AnyRef]
    val inferInputs = new util.ArrayList[util.Map[String, AnyRef]]
    val requestId = UUID.randomUUID.toString
    val inputData = new RequestInput(requestId)
    // creating modelInfer map that same as kserve v2 existing request input data
    modelInferMap.put("id", request.id)
    modelInferMap.put("model_name", request.modelName)
  
    for (entry <- request.inputs) {
      val inferInputMap = new util.HashMap[String, AnyRef]
      inferInputMap.put("name", entry.name)
      inferInputMap.put("shape", entry.shape)
      inferInputMap.put("datatype", entry.datatype)
      OpenInferenceProtocolImpl.setInputContents(entry, inferInputMap)
      inferInputs.add(inferInputMap)
    }
    modelInferMap.put("inputs", inferInputs)
    val jsonString = gson.toJson(modelInferMap)
    val byteArray = jsonString.getBytes(StandardCharsets.UTF_8)
    if (modelName == null || "" == modelName) {
      val e = new BadRequestException("Parameter model_name is required.")
      sendErrorResponse(responseObserver, Status.INTERNAL, e, "BadRequestException.()")
      return
    }
    if (modelVersion == null || "" == modelVersion) modelVersion = null
    try {
      val modelManager = ModelManager.getInstance
      inputData.addParameter(new InputParameter("body", byteArray, contentsType))
      val job = new GRPCJob(responseObserver, modelName, modelVersion, inputData, WorkerCommands.OIPPREDICT)
      if (!modelManager.addJob(job)) {
        val responseMessage = ApiUtils.getStreamingInferenceErrorResponseMessage(modelName, modelVersion)
        val e = new InternalServerException(responseMessage)
        sendErrorResponse(responseObserver, Status.INTERNAL, e, "InternalServerException.()")
      }
    } catch {
      case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
        sendErrorResponse(responseObserver, Status.INTERNAL, e, null)
    }
  }

  /** The ServerLive API indicates if the inference server is able to receive 
   * and respond to metadata and inference requests.
   */
  override def serverLive(request: ServerLiveRequest) = ???

  /** The ServerReady API indicates if the server is ready for inferencing.
   */
  override def serverReady(request: ServerReadyRequest) = ???

  /** The ModelReady API indicates if a specific model is ready for inferencing.
   */
  override def modelReady(request: ModelReadyRequest) = ???

  /** The ServerMetadata API provides information about the server. Errors are 
   * indicated by the google.rpc.Status returned for the request. The OK code 
   * indicates success and other codes indicate failure.
   */
  override def serverMetadata(request: ServerMetadataRequest) = ???

  /** The per-model metadata API provides information about a model. Errors are 
   * indicated by the google.rpc.Status returned for the request. The OK code 
   * indicates success and other codes indicate failure.
   */
  override def modelMetadata(request: ModelMetadataRequest) = ???

  /** The ModelInfer API performs inference using the specified model. Errors are
   * indicated by the google.rpc.Status returned for the request. The OK code 
   * indicates success and other codes indicate failure.
   */
  override def modelInfer(request: ModelInferRequest) = ???

  override def bindService(): ServerServiceDefinition = ???
}
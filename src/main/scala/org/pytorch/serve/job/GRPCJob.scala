package org.pytorch.serve.job

import com.google.gson.{Gson, JsonArray, JsonElement, JsonObject}
import com.google.protobuf.{Any, ByteString}
import com.google.rpc.ErrorInfo
import io.grpc.Status
import io.grpc.stub.{ServerCallStreamObserver, StreamObserver}
import org.pytorch.serve.archive.model.{ModelNotFoundException, ModelVersionNotFoundException}
import org.pytorch.serve.grpc.openinference.open_inference_grpc.ModelInferResponse.InferOutputTensor
import org.pytorch.serve.grpc.openinference.open_inference_grpc.{InferTensorContents, ModelInferResponse}

import java.util
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
//import org.pytorch.serve.grpc.inference.PredictionResponse
import org.pytorch.serve.grpc.inference.inference.PredictionResponse
//import org.pytorch.serve.grpc.management.ManagementResponse
import org.pytorch.serve.grpc.management.management.ManagementResponse
//import org.pytorch.serve.grpc.openinference.OpenInferenceGrpc.InferTensorContents
//import org.pytorch.serve.grpc.openinference.OpenInferenceGrpc.ModelInferResponse
//import org.pytorch.serve.grpc.openinference.OpenInferenceGrpc.ModelInferResponse.InferOutputTensor
import org.pytorch.serve.grpcimpl.ManagementImpl
import org.pytorch.serve.http.messages.DescribeModelResponse
import org.pytorch.serve.metrics.{IMetric, MetricCache}
import org.pytorch.serve.util.messages.WorkerCommands.*
import org.pytorch.serve.util.messages.{RequestInput, WorkerCommands}
import org.pytorch.serve.util.{ApiUtils, ConfigManager, GRPCUtils, JsonUtils}
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.*
object GRPCJob {
  private val logger = LoggerFactory.getLogger(classOf[GRPCJob])
}

class GRPCJob(modelName:String, version:String, cmd:WorkerCommands, input:RequestInput) extends Job(modelName, version, cmd, input) {
  final private var queueTimeMetric: IMetric = null
  final private var queueTimeMetricDimensionValues: List[String] = null
  private var predictionResponseObserver: StreamObserver[PredictionResponse] = null
  private var managementResponseObserver: StreamObserver[ManagementResponse] = null
  private var modelInferResponseObserver: StreamObserver[ModelInferResponse] = null

  def this(predictionResponseObserver: StreamObserver[PredictionResponse], modelName: String, version: String, cmd: WorkerCommands, input: RequestInput) ={
    this(modelName, version, cmd, input)
    this.predictionResponseObserver = predictionResponseObserver
    this.queueTimeMetric = MetricCache.getInstance.getMetricFrontend("QueueTime")
    this.queueTimeMetricDimensionValues = util.Arrays.asList("Host", ConfigManager.getInstance.getHostName).asScala.toList
  }

  def this(modelInferResponseObserver: StreamObserver[ModelInferResponse], modelName: String, version: String, input: RequestInput, cmd: WorkerCommands) ={
    this(modelName, version, cmd, input)
    this.modelInferResponseObserver = modelInferResponseObserver
    this.queueTimeMetric = MetricCache.getInstance.getMetricFrontend("QueueTime")
    this.queueTimeMetricDimensionValues = util.Arrays.asList("Host", ConfigManager.getInstance.getHostName).asScala.toList
  }

  def this(managementResponseObserver: StreamObserver[ManagementResponse], modelName: String, version: String, input: RequestInput)= {
    this(modelName, version, WorkerCommands.DESCRIBE, input)
    this.managementResponseObserver = managementResponseObserver
    this.queueTimeMetric = MetricCache.getInstance.getMetricFrontend("QueueTime")
    this.queueTimeMetricDimensionValues = util.Arrays.asList("Host", ConfigManager.getInstance.getHostName).asScala.toList
  }

  private def cancelHandler(responseObserver: ServerCallStreamObserver[PredictionResponse]): Boolean = {
    if (responseObserver.isCancelled) {
      GRPCJob.logger.warn("grpc client call already cancelled, not able to send this response for requestId: {}", getPayload.getRequestId)
      return true
    }
    false
  }

  private def logQueueTime(): Unit = {
    GRPCJob.logger.debug("Waiting time ns: {}, Backend time ns: {}", getScheduled - getBegin, System.nanoTime - getScheduled)
    val queueTime = TimeUnit.MILLISECONDS.convert(getScheduled - getBegin, TimeUnit.NANOSECONDS).toDouble
    if (this.queueTimeMetric != null) try this.queueTimeMetric.addOrUpdate(this.queueTimeMetricDimensionValues, queueTime)
    catch {
      case e: Exception =>
        GRPCJob.logger.error("Failed to update frontend metric QueueTime: ", e)
    }
  }

  override def response(body: Array[Byte], contentType: CharSequence, statusCode: Int, statusPhrase: String, responseHeaders: Map[String, String]): Unit = {
    val output = ByteString.copyFrom(body)
    val cmd = this.getCmd
    cmd match {
      case PREDICT =>
      case STREAMPREDICT =>
      case STREAMPREDICT2 =>
        val responseObserver = predictionResponseObserver.asInstanceOf[ServerCallStreamObserver[PredictionResponse]]
        if (cancelHandler(responseObserver)) {
          // issue #3087: Leave response early as the request has been canceled.
          // Note: trying to continue wil trigger an exception when calling `onNext`.
          return
        }
        val reply = PredictionResponse.defaultInstance
        reply.withPrediction(output)//newBuilder.setPrediction(output).build
        responseObserver.onNext(reply)
        if ((cmd eq WorkerCommands.PREDICT) || ((cmd eq WorkerCommands.STREAMPREDICT) && responseHeaders.get(RequestInput.TS_STREAM_NEXT).get == "false")) {
          if (cancelHandler(responseObserver)) return
          responseObserver.onCompleted()
          logQueueTime()
        }
        else if ((cmd eq WorkerCommands.STREAMPREDICT2) && (responseHeaders.get(RequestInput.TS_STREAM_NEXT) == null || responseHeaders.get(RequestInput.TS_STREAM_NEXT).get == "false")) logQueueTime()
      case DESCRIBE =>
        try {
          val respList = ApiUtils.getModelDescription(this.getModelName, this.getModelVersion)
          if (!output.isEmpty && respList != null && respList.size == 1) respList(0).setCustomizedMetadata(body)
          val resp = JsonUtils.GSON_PRETTY.toJson(respList)
          val mgmtReply = ManagementResponse.of(resp) //newBuilder.setMsg(resp).build
          managementResponseObserver.onNext(mgmtReply)
          managementResponseObserver.onCompleted()
        } catch {
          case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
            ManagementImpl.sendErrorResponse(managementResponseObserver, Status.NOT_FOUND, e)
        }
      case OIPPREDICT =>
        val gson = new Gson
        val jsonResponse = output.toStringUtf8
        val jsonObject = gson.fromJson(jsonResponse, classOf[JsonObject])
        if (modelInferResponseObserver.asInstanceOf[ServerCallStreamObserver[ModelInferResponse]].isCancelled) {
          GRPCJob.logger.warn("grpc client call already cancelled, not able to send this response for requestId: {}", getPayload.getRequestId)
          return
        }
        val id = jsonObject.get("id").getAsString
        val modelName = jsonObject.get("model_name").getAsString
        val modelVersion = jsonObject.get("model_version").getAsString
        val responseBuilder = ModelInferResponse.defaultInstance //newBuilder
        responseBuilder.withId(id)
        responseBuilder.withModelName(modelName)
        responseBuilder.withModelVersion(modelVersion)
//        responseBuilder.setId(jsonObject.get("id").getAsString)
//        responseBuilder.setModelName(jsonObject.get("model_name").getAsString)
//        responseBuilder.setModelVersion(jsonObject.get("model_version").getAsString)
        val jsonOutputs:JsonArray = jsonObject.get("outputs").getAsJsonArray
//        import scala.collection.JavaConversions._
        for (element <- jsonOutputs.asScala) {
          val outputBuilder = InferOutputTensor.defaultInstance //.newBuilder
          outputBuilder.withName(element.getAsJsonObject.get("name").getAsString)
          outputBuilder.withDatatype(element.getAsJsonObject.get("datatype").getAsString)
          val shapeArray = element.getAsJsonObject.get("shape").getAsJsonArray
          shapeArray.forEach((shapeElement: JsonElement) => outputBuilder.addShape(shapeElement.getAsLong))
          setOutputContents(element, outputBuilder)
          responseBuilder.addOutputs(outputBuilder)
        }
        modelInferResponseObserver.onNext(responseBuilder)
        modelInferResponseObserver.onCompleted()
      case _ =>
    }
  }

  override def sendError(status: Int, error: String): Unit = {
    val responseStatus = GRPCUtils.getGRPCStatusCode(status)
    val cmd = this.getCmd
    cmd match {
      case PREDICT|STREAMPREDICT |STREAMPREDICT2=>
        val responseObserver = predictionResponseObserver.asInstanceOf[ServerCallStreamObserver[PredictionResponse]]
        if (cancelHandler(responseObserver)) {
          // issue #3087: Leave response early as the request has been canceled.
          // Note: trying to continue wil trigger an exception when calling `onNext`.
          return
        }
        if ((cmd eq WorkerCommands.PREDICT) || (cmd eq WorkerCommands.STREAMPREDICT)) responseObserver.onError(responseStatus.withDescription(error).augmentDescription("org.pytorch.serve.http.InternalServerException").asRuntimeException)
        else if (cmd eq WorkerCommands.STREAMPREDICT2) {
          val rpcStatus = com.google.rpc.Status.newBuilder.setCode(responseStatus.getCode.value).setMessage(error).addDetails(Any.pack(ErrorInfo.newBuilder.setReason("org.pytorch.serve.http.InternalServerException").build)).build
          val predRep = PredictionResponse.defaultInstance
          predRep.withStatus(null) //rpcStatus) // todo scalapb generate bug
          predRep.withPrediction(null)
          responseObserver.onNext(predRep) //PredictionResponse.newBuilder.setPrediction(null).setStatus(rpcStatus).build)
        }
      case DESCRIBE =>
        managementResponseObserver.onError(responseStatus.withDescription(error).augmentDescription("org.pytorch.serve.http.InternalServerException").asRuntimeException)
      case OIPPREDICT =>
        modelInferResponseObserver.onError(responseStatus.withDescription(error).augmentDescription("org.pytorch.serve.http.InternalServerException").asRuntimeException)
      case _ =>
    }
  }

  override def isOpen: Boolean = predictionResponseObserver.asInstanceOf[ServerCallStreamObserver[PredictionResponse]].isCancelled

  private def setOutputContents(element: JsonElement, outputBuilder: InferOutputTensor): Unit = {
    val dataType = element.getAsJsonObject.get("datatype").getAsString
    val jsonData = element.getAsJsonObject.get("data").getAsJsonArray
    val inferTensorContents = InferTensorContents.defaultInstance //.newBuilder
    dataType match {
//      case "INT8" => // jump to INT32 case
//      case "INT16" => // jump to INT32 case
      case "INT8"|"INT16"|"INT32" => // intContents
//        val int32Contents = new util.ArrayList[Integer]
//        jsonData.asScala.map(data => data.getAsInt)
//        jsonData.forEach((data: JsonElement) => int32Contents.add(data.getAsInt.toLong.toInt))
        inferTensorContents.addAllIntContents(jsonData.asScala.map(data => data.getAsInt))
      case "INT64" => // int64Contents
        val int64Contents = new ListBuffer[Long]
        jsonData.forEach((data: JsonElement) => int64Contents.append(data.getAsLong))
        inferTensorContents.addAllInt64Contents(jsonData.asScala.map(data => data.getAsLong))
      case "BYTES" => // bytesContents
        val byteContents = new ListBuffer[ByteString]
        jsonData.forEach((data: JsonElement) => byteContents.append(ByteString.copyFromUtf8(data.toString)))
        inferTensorContents.addAllBytesContents(byteContents)
      case "BOOL" => // boolContents
//        val boolContents = new util.ArrayList[Boolean]
//        jsonData.forEach((data: JsonElement) => boolContents.add(data.getAsBoolean))
        inferTensorContents.addAllBoolContents(jsonData.asScala.map(data => data.getAsBoolean))
      case "FP32" => // fp32Contents
//        val fp32Contents = new util.ArrayList[Float]
//        jsonData.forEach((data: JsonElement) => fp32Contents.add(data.getAsFloat))
        inferTensorContents.addAllFp32Contents(jsonData.asScala.map(data => data.getAsFloat))
      case "FP64" => // fp64Contents
//        val fp64Contents = new util.ArrayList[Double]
//        jsonData.forEach((data: JsonElement) => fp64Contents.add(data.getAsDouble))
        inferTensorContents.addAllFp64Contents(jsonData.asScala.map(data => data.getAsDouble))
//      case "UINT8" => // jump to UINT32 case
//      case  => // jump to UINT32 case
      case "UINT8"|"UINT16"|"UINT32"=> // uint32Contents
//        val uint32Contents = new util.ArrayList[Integer]
//        jsonData.forEach((data: JsonElement) => uint32Contents.add(data.getAsInt))
        inferTensorContents.addAllUintContents(jsonData.asScala.map(data => data.getAsInt))
      case "UINT64" => // uint64Contents
//        val uint64Contents = new util.ArrayList[Long]
//        jsonData.forEach((data: JsonElement) => uint64Contents.add(data.getAsLong))
        inferTensorContents.addAllUint64Contents(jsonData.asScala.map(data => data.getAsLong))
      case _ =>
    }
    outputBuilder.withContents(inferTensorContents)
//    outputBuilder.setContents(inferTensorContents) // set output contents
  }
}
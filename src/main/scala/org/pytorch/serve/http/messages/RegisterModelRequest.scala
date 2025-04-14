package org.pytorch.serve.http.messages

import com.google.gson.annotations.SerializedName
import io.netty.handler.codec.http.QueryStringDecoder
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.GRPCUtils
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.grpc.management.management.RegisterModelRequest as REQ
/** Register Model Request for Model server */
object RegisterModelRequest {
  val DEFAULT_BATCH_SIZE = 1
  val DEFAULT_MAX_BATCH_DELAY = 100
}

class RegisterModelRequest {
//  batchSize = -1 * RegisterModelRequest.DEFAULT_BATCH_SIZE
//  maxBatchDelay = -100 * RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY
//  synchronous = true
//  initialWorkers = ConfigManager.getInstance.getConfiguredDefaultWorkersPerModel
//  responseTimeout = -1
//  startupTimeout = -1
//  s3SseKms = false
  @SerializedName("model_name") 
  private var modelName:String = null
  @SerializedName("runtime") 
  private var runtime:String = null
  @SerializedName("handler") 
  private var handler:String = null
  @SerializedName("batch_size")
  private var batchSize: Int = -1 * RegisterModelRequest.DEFAULT_BATCH_SIZE
  @SerializedName("max_batch_delay")
  private var maxBatchDelay: Int = -100 * RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY
  @SerializedName("initial_workers")
  private var initialWorkers: Int = ConfigManager.getInstance.getConfiguredDefaultWorkersPerModel
  @SerializedName("synchronous") 
  private var synchronous = true
  @SerializedName("response_timeout") 
  private var responseTimeout:Int = -1
  @SerializedName("startup_timeout")
  private var startupTimeout: Int = -1
  @SerializedName("url") 
  private var modelUrl:String = null
  @SerializedName("s3_sse_kms")
  private var s3SseKms: Boolean = false

  def this(decoder: QueryStringDecoder) ={
    this()
    modelName = NettyUtils.getParameter(decoder, "model_name", null)
    runtime = NettyUtils.getParameter(decoder, "runtime", null)
    handler = NettyUtils.getParameter(decoder, "handler", null)
    batchSize = NettyUtils.getIntParameter(decoder, "batch_size", -1 * RegisterModelRequest.DEFAULT_BATCH_SIZE)
    maxBatchDelay = NettyUtils.getIntParameter(decoder, "max_batch_delay", -1 * RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY)
    initialWorkers = NettyUtils.getIntParameter(decoder, "initial_workers", ConfigManager.getInstance.getConfiguredDefaultWorkersPerModel)
    synchronous = java.lang.Boolean.parseBoolean(NettyUtils.getParameter(decoder, "synchronous", "true"))
    responseTimeout = NettyUtils.getIntParameter(decoder, "response_timeout", -1)
    startupTimeout = NettyUtils.getIntParameter(decoder, "startup_timeout", -1)
    modelUrl = NettyUtils.getParameter(decoder, "url", null)
    s3SseKms = java.lang.Boolean.parseBoolean(NettyUtils.getParameter(decoder, "s3_sse_kms", "false"))
  }

  def this(request: REQ) ={
    this()
    modelName = GRPCUtils.getRegisterParam(request.modelName, null)
    runtime = GRPCUtils.getRegisterParam(request.runtime, null)
    handler = GRPCUtils.getRegisterParam(request.handler, null)
    batchSize = GRPCUtils.getRegisterParam(request.batchSize, -1 * RegisterModelRequest.DEFAULT_BATCH_SIZE)
    maxBatchDelay = GRPCUtils.getRegisterParam(request.maxBatchDelay, -1 * RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY)
    initialWorkers = GRPCUtils.getRegisterParam(request.initialWorkers, ConfigManager.getInstance.getConfiguredDefaultWorkersPerModel)
    synchronous = request.synchronous
    responseTimeout = GRPCUtils.getRegisterParam(request.responseTimeout, -1)
    startupTimeout = GRPCUtils.getRegisterParam(request.startupTimeout, -1)
    modelUrl = GRPCUtils.getRegisterParam(request.url, null)
    s3SseKms = request.s3SseKms
  }

  def getModelName: String = modelName

  def getRuntime: String = runtime

  def getHandler: String = handler

  def getBatchSize: Integer = batchSize

  def getMaxBatchDelay: Integer = maxBatchDelay

  def getInitialWorkers: Integer = initialWorkers

  def getSynchronous: Boolean = synchronous

  def getResponseTimeout: Integer = responseTimeout

  def getStartupTimeout: Integer = startupTimeout

  def getModelUrl: String = modelUrl

  def getS3SseKms: Boolean = s3SseKms
}
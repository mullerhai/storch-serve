package org.pytorch.serve.util

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpResponseStatus
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.{Manifest, ModelArchive, ModelException, ModelNotFoundException, ModelVersionNotFoundException}
import org.pytorch.serve.archive.utils.ArchiveUtils
import org.pytorch.serve.http.*
import org.pytorch.serve.http.messages.{DescribeModelResponse, ListModelsResponse, RegisterModelRequest}
import org.pytorch.serve.job.RestJob
import org.pytorch.serve.snapshot.SnapshotManager
import org.pytorch.serve.util.messages.{RequestInput, WorkerCommands}
import org.pytorch.serve.wlm.*

import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.FileAlreadyExistsException
import java.util
import java.util.Collections
import java.util.concurrent.{CompletableFuture, ExecutionException}
import java.util.function.Function
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

object ApiUtils {
  def getModelList(limitTmp: Int, pageTokenTmp: Int): ListModelsResponse = {
    val limit = if (limitTmp > 100 || limitTmp < 0) then 100 else limitTmp
    val pageToken = if (pageTokenTmp < 0) then 0 else pageTokenTmp
    val models = ModelManager.getInstance.getDefaultModels(true)
    val keys = new ListBuffer[String]() //models.keySet)
    models.keySet.map(ele => keys.append(ele))
    //    Collections.sort(keys)
    keys.sorted
    val list = new ListModelsResponse
    var last = pageToken + limit
    if (last > keys.size) last = keys.size
    else list.setNextPageToken(String.valueOf(last))
    for (i <- pageToken until last) {
      val modelName = keys(i)
      val model = models.get(modelName)
      if model.isDefined then list.addModel(modelName, model.get.getModelUrl)
    }
    list
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  def getModelDescription(modelName: String, modelVersion: String): List[DescribeModelResponse] = {
    val modelManager = ModelManager.getInstance
    val resp = new ListBuffer[DescribeModelResponse]
    if ("all" == modelVersion) {
//      import scala.collection.JavaConversions._
      for (m <- modelManager.getAllModelVersions(modelName)) {
        resp.append(createModelResponse(modelManager, modelName, m._2))
      }
    }
    else {
      val model = modelManager.getModel(modelName, modelVersion)
      if (model == null) throw new ModelNotFoundException("Model not found: " + modelName)
      resp.append(createModelResponse(modelManager, modelName, model))
    }
    resp.toList
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  def setDefault(modelName: String, newModelVersion: String): String = {
    val modelManager = ModelManager.getInstance
    modelManager.setDefaultVersion(modelName, newModelVersion)
    val msg = "Default version successfully updated for model \"" + modelName + "\" to \"" + newModelVersion + "\""
    SnapshotManager.getInstance.saveSnapshot()
    msg
  }

  @throws[ModelException]
  @throws[InternalServerException]
  @throws[ExecutionException]
  @throws[InterruptedException]
  @throws[DownloadArchiveException]
  @throws[WorkerInitializationException]
  def registerModel(registerModelRequest: RegisterModelRequest): StatusResponse = {
    val modelUrl = registerModelRequest.getModelUrl
    if (modelUrl == null) throw new BadRequestException("Parameter url is required.")
    val modelName = registerModelRequest.getModelName
    val runtime = registerModelRequest.getRuntime
    val handler = registerModelRequest.getHandler
    val batchSize = registerModelRequest.getBatchSize
    val maxBatchDelay = registerModelRequest.getMaxBatchDelay
    val initialWorkers = registerModelRequest.getInitialWorkers
    var responseTimeout = registerModelRequest.getResponseTimeout
    var startupTimeout = registerModelRequest.getStartupTimeout
    val s3SseKms = registerModelRequest.getS3SseKms
    if (responseTimeout == -1) responseTimeout = ConfigManager.getInstance.getDefaultResponseTimeout
    if (startupTimeout == -1) startupTimeout = ConfigManager.getInstance.getDefaultStartupTimeout
    var runtimeType: Manifest.RuntimeType = null
    if (runtime != null) try runtimeType = Manifest.RuntimeType.valueOf(runtime)
    catch {
      case e: IllegalArgumentException =>
        throw new BadRequestException(e)
    }
    handleRegister(modelUrl, modelName, runtimeType, handler, batchSize, maxBatchDelay, responseTimeout, startupTimeout, initialWorkers, registerModelRequest.getSynchronous, false, s3SseKms)
  }

  @throws[ModelException]
  @throws[ExecutionException]
  @throws[InterruptedException]
  @throws[DownloadArchiveException]
  @throws[WorkerInitializationException]
  def handleRegister(modelUrl: String, modelNamez: String, runtimeType: Manifest.RuntimeType, handler: String, batchSize: Int, maxBatchDelay: Int, responseTimeout: Int, startupTimeout: Int, initialWorkers: Int, isSync: Boolean, isWorkflowModel: Boolean, s3SseKms: Boolean): StatusResponse = {
    val modelManager = ModelManager.getInstance
    var archive: ModelArchive = null
    var modelName: String = modelNamez
    try archive = modelManager.registerModel(modelUrl, modelName, runtimeType, handler, batchSize, maxBatchDelay, responseTimeout, startupTimeout, null, false, isWorkflowModel, s3SseKms)
    catch {
      case e: FileAlreadyExistsException =>
        throw new InternalServerException("Model file already exists " + ArchiveUtils.getFilenameFromUrl(modelUrl), e)
      case e@(_: IOException | _: InterruptedException) =>
        throw new InternalServerException("Failed to save model: " + modelUrl, e)
    }
    modelName = archive.getModelName
    var minWorkers = 0
    var maxWorkers = 0
    if (archive.getModelConfig != null) {
      val marMinWorkers = archive.getModelConfig.getMinWorkers
      val marMaxWorkers = archive.getModelConfig.getMaxWorkers
      if (marMinWorkers > 0 && marMaxWorkers >= marMinWorkers) {
        minWorkers = marMinWorkers
        maxWorkers = marMaxWorkers
      }
    }
    if (initialWorkers <= 0 && minWorkers == 0) {
      val msg = "Model \"" + modelName + "\" Version: " + archive.getModelVersion + " registered with 0 initial workers. Use scale workers API to add workers for the model."
      if (!isWorkflowModel) SnapshotManager.getInstance.saveSnapshot()
      return new StatusResponse(msg, HttpURLConnection.HTTP_OK)
    }
    minWorkers = if (minWorkers > 0) minWorkers
    else initialWorkers
    maxWorkers = if (maxWorkers > 0) maxWorkers
    else initialWorkers
    ApiUtils.updateModelWorkers(modelName, archive.getModelVersion, minWorkers, maxWorkers, isSync, true, (f: Void) => {
      modelManager.unregisterModel(archive.getModelName, archive.getModelVersion)
      null
    })
  }

  private def createModelResponse(modelManager: ModelManager, modelName: String, model: Model) = {
    val resp = new DescribeModelResponse
    resp.setModelName(modelName)
    resp.setModelUrl(model.getModelUrl)
    resp.setBatchSize(model.getBatchSize)
    resp.setMaxBatchDelay(model.getMaxBatchDelay.toInt)
    resp.setMaxWorkers(model.getMaxWorkers)
    resp.setMinWorkers(model.getMinWorkers)
    resp.setLoadedAtStartup(modelManager.getStartupModels.contains(modelName))
    val manifest = model.getModelArchive.getManifest
    resp.setModelVersion(manifest.getModel.getModelVersion)
    resp.setRuntime(manifest.getRuntime.toString)
    resp.setResponseTimeout(model.getResponseTimeout)
    resp.setStartupTimeout(model.getStartupTimeout)
    resp.setMaxRetryTimeoutInSec(model.getMaxRetryTimeoutInMill / 1000)
    resp.setClientTimeoutInMills(model.getClientTimeoutInMills)
    resp.setParallelType(model.getParallelType.toString)
    resp.setParallelLevel(model.getParallelLevel)
    resp.setDeviceType(model.getDeviceType.toString)
    resp.setDeviceIds(model.getDeviceIds)
    resp.setContinuousBatching(model.isContinuousBatching)
    resp.setUseJobTicket(model.isUseJobTicket)
    resp.setUseVenv(model.isUseVenv)
    resp.setStateful(model.isSequenceBatching)
    resp.setSequenceMaxIdleMSec(model.getSequenceMaxIdleMSec)
    resp.setSequenceTimeoutMSec(model.getSequenceTimeoutMSec)
    resp.setMaxNumSequence(model.getMaxNumSequence)
    resp.setMaxSequenceJobQueueSize(model.getMaxSequenceJobQueueSize)
    val workers = modelManager.getWorkers(model.getModelVersionName)
    for (worker <- workers) {
      val workerId = worker.getWorkerId
      val startTime = worker.getStartTime
      val isRunning = worker.isRunning && (worker.getState eq WorkerState.WORKER_MODEL_LOADED)
      val gpuId = worker.getGpuId
      val memory = worker.getMemory
      val pid = worker.getPid
      val gpuUsage = worker.getGpuUsage
      resp.addWorker(workerId, startTime, isRunning, gpuId, memory, pid, gpuUsage)
    }
    val jobQueueStatus = new DescribeModelResponse.JobQueueStatus
    jobQueueStatus.setRemainingCapacity(model.getJobQueueRemainingCapacity)
    jobQueueStatus.setPendingRequests(model.getPendingRequestsInJobQueue)
    resp.setJobQueueStatus(jobQueueStatus)
    resp
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  def unregisterModel(modelName: String, modelVersion: String): Unit = {
    val modelManager = ModelManager.getInstance
    val httpResponseStatus = modelManager.unregisterModel(modelName, modelVersion)
    if (httpResponseStatus == HttpResponseStatus.NOT_FOUND.code) throw new ModelNotFoundException("Model not found: " + modelName)
    else if (httpResponseStatus == HttpResponseStatus.BAD_REQUEST.code) throw new ModelVersionNotFoundException(String.format("Model version: %s does not exist for model: %s", modelVersion, modelName))
    else if (httpResponseStatus == HttpResponseStatus.INTERNAL_SERVER_ERROR.code) throw new InternalServerException("Interrupted while cleaning resources: " + modelName)
    else if (httpResponseStatus == HttpResponseStatus.REQUEST_TIMEOUT.code) throw new RequestTimeoutException("Timed out while cleaning resources: " + modelName)
    else if (httpResponseStatus == HttpResponseStatus.FORBIDDEN.code) throw new InvalidModelVersionException("Cannot remove default version for model " + modelName)
  }

  def getTorchServeHealth(r: Runnable): Unit = {
    val modelManager = ModelManager.getInstance
    modelManager.submitTask(r)
  }

  @throws[ModelVersionNotFoundException]
  @throws[ModelNotFoundException]
  @throws[ExecutionException]
  @throws[InterruptedException]
  @throws[WorkerInitializationException]
  def updateModelWorkers(modelName: String, modelVersion: String, minWorkers: Int, maxWorkers: Int, synchronous: Boolean, isInit: Boolean, onError: Function[Void, Void]): StatusResponse = {
    val modelManager = ModelManager.getInstance
    if (maxWorkers < minWorkers) throw new BadRequestException("max_worker cannot be less than min_worker.")
    if (!modelManager.getDefaultModels.contains(modelName)) throw new ModelNotFoundException("Model not found: " + modelName)
    val future = modelManager.updateModel(modelName, modelVersion, minWorkers, maxWorkers)
    val statusResponse = new StatusResponse
    if (!synchronous) return new StatusResponse("Processing worker updates...", HttpURLConnection.HTTP_ACCEPTED)
    val statusResponseCompletableFuture = future.thenApply((v: Integer) => {
      val status = modelManager.scaleRequestStatus(modelName, modelVersion)
      if (HttpURLConnection.HTTP_OK == v) if (status) {
        var msg = "Workers scaled to " + minWorkers + " for model: " + modelName
        if (modelVersion != null) msg += ", version: " + modelVersion // NOPMD
        if (isInit) msg = "Model \"" + modelName + "\" Version: " + modelVersion + " registered with " + minWorkers + " initial workers"
        statusResponse.setStatus(msg)
        statusResponse.setHttpResponseCode(v)
      }
      else {
        statusResponse.setStatus("Workers scaling in progress...")
        statusResponse.setHttpResponseCode(HttpURLConnection.HTTP_PARTIAL)
      }
      else {
        statusResponse.setHttpResponseCode(v)
        val msg = "Failed to start workers for model " + modelName + " version: " + modelVersion
        statusResponse.setStatus(msg)
        statusResponse.setE(new InternalServerException(msg))
        if (onError != null) onError.apply(null)
      }
      statusResponse
    }).exceptionally((e: Throwable) => {
      if (onError != null) onError.apply(null)
      statusResponse.setStatus(e.getMessage)
      statusResponse.setHttpResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
      statusResponse.setE(e)
      statusResponse
    })
    statusResponseCompletableFuture.get
  }

  def getWorkerStatus: String = {
    val modelManager = ModelManager.getInstance
    var response = "Healthy"
    var numWorking = 0
    var numScaled = 0
    for (m <- modelManager.getAllModels.toSeq) {
      numScaled += m._2.getDefaultModel.getMinWorkers
      numWorking += modelManager.getNumRunningWorkers(m._2.getDefaultModel.getModelVersionName)
    }
    if ((numWorking > 0) && (numWorking < numScaled)) response = "Partial Healthy"
    else if ((numWorking == 0) && (numScaled > 0)) response = "Unhealthy"
    // TODO: Check if its OK to send other 2xx errors to ALB for "Partial Healthy"
    // and
    // "Unhealthy"
    response
  }

  def isModelHealthy: Boolean = {
    val modelManager = ModelManager.getInstance
    var numHealthy = 0
    var numScaled = 0
    for (m <- modelManager.getAllModels.toSeq) {
      numScaled = m._2.getDefaultModel.getMinWorkers
      numHealthy = modelManager.getNumHealthyWorkers(m._2.getDefaultModel.getModelVersionName)
      if (numHealthy < numScaled) return false
    }
    true
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  def addRESTInferenceJob(ctx: ChannelHandlerContext, modelName: String, version: String, input: RequestInput): RestJob = {
    val job = new RestJob(ctx, modelName, version, WorkerCommands.PREDICT, input)
    if (!ModelManager.getInstance.addJob(job)) {
      val responseMessage = getStreamingInferenceErrorResponseMessage(modelName, version)
      throw new ServiceUnavailableException(responseMessage)
    }
    job
  }

  @SuppressWarnings(Array("PMD")) def getStreamingInferenceErrorResponseMessage(modelName: String, modelVersion: String): String = {
    val responseMessage = new StringBuilder().append("Model \"").append(modelName)
    if (modelVersion != null) responseMessage.append("\" Version ").append(modelVersion)
    responseMessage.append("\" has no worker to serve inference request. Please use scale workers API to add workers. " + "If this is a sequence inference, please check if it is closed, or expired;" + " or exceeds maxSequenceJobQueueSize")
    responseMessage.toString
  }

  def getDescribeErrorResponseMessage(modelName: String): String = {
    var responseMessage = "Model \"" + modelName
    responseMessage += "\" has no worker to serve describe request. Please use scale workers API to add workers."
    responseMessage
  }
}

final class ApiUtils private {
}
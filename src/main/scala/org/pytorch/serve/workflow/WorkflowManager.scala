package org.pytorch.serve.workflow

import scala.jdk.CollectionConverters.*
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion

import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.archive.model.ModelVersionNotFoundException
import org.pytorch.serve.archive.workflow.InvalidWorkflowException
import org.pytorch.serve.archive.workflow.WorkflowArchive
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.archive.workflow.WorkflowNotFoundException
import org.pytorch.serve.ensemble.DagExecutor
import org.pytorch.serve.ensemble.InvalidDAGException
import org.pytorch.serve.ensemble.Node
import org.pytorch.serve.ensemble.NodeOutput
import org.pytorch.serve.ensemble.WorkFlow
import org.pytorch.serve.ensemble.WorkflowModel
import org.pytorch.serve.http.BadRequestException
import org.pytorch.serve.http.ConflictStatusException
import org.pytorch.serve.http.InternalServerException
import org.pytorch.serve.http.StatusResponse
import org.pytorch.serve.util.ApiUtils
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.util.messages.RequestInput
import org.pytorch.serve.workflow.messages.ModelRegistrationResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory


object WorkflowManager {
  private val logger = LoggerFactory.getLogger(classOf[WorkflowManager])
  private var workflowManager: WorkflowManager = null

  def init(configManager: ConfigManager): Unit = {
    workflowManager = new WorkflowManager(configManager)
  }

  def getInstance: WorkflowManager = workflowManager
}

final class WorkflowManager private(private val configManager: ConfigManager) {
  
  final private val namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("wf-manager-thread-%d").build
  final private val inferenceExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors, namedThreadFactory)
  final private var workflowMap: ConcurrentHashMap[String, WorkFlow] = new ConcurrentHashMap[String, WorkFlow]

  @throws[DownloadArchiveException]
  @throws[IOException]
  @throws[WorkflowException]
  private def createWorkflowArchive(workflowName: String, url: String):WorkflowArchive = createWorkflowArchive(workflowName, url, false)

  @throws[DownloadArchiveException]
  @throws[IOException]
  @throws[WorkflowException]
  private def createWorkflowArchive(workflowName: String, url: String, s3SseKmsEnabled: Boolean) :WorkflowArchive= {
    val archive = WorkflowArchive.downloadWorkflow(configManager.getAllowedUrls, configManager.getWorkflowStore, url, s3SseKmsEnabled)
    if (!(workflowName == null || workflowName.isEmpty)) archive.getManifest.getWorkflow.setWorkflowName(workflowName)
    archive.validate()
    archive
  }

  @throws[IOException]
  @throws[InvalidDAGException]
  @throws[InvalidWorkflowException]
  private def createWorkflow(archive: WorkflowArchive) = new WorkFlow(archive)

  @throws[WorkflowException]
  def registerWorkflow(workflowName: String, url: String, responseTimeout: Int, startupTimeout: Int, synchronous: Boolean): StatusResponse = registerWorkflow(workflowName, url, responseTimeout, startupTimeout, synchronous, false)

  @throws[WorkflowException]
  def registerWorkflow(workflowName: String, url: String, responseTimeout: Int, startupTimeout: Int, synchronous: Boolean, s3SseKms: Boolean): StatusResponse = {
    if (url == null) throw new BadRequestException("Parameter url is required.")
    val status = new StatusResponse
    val executorService = Executors.newFixedThreadPool(4)
    val executorCompletionService = new ExecutorCompletionService[ModelRegistrationResult](executorService)
    var failed = false
    val failedMessages = new util.ArrayList[String]
    val successNodes = new util.ArrayList[String]
    try {
      val archive = createWorkflowArchive(workflowName, url)
      val workflow = createWorkflow(archive)
      if (workflowMap.get(workflow.getWorkflowArchive.getWorkflowName) != null) throw new ConflictStatusException("Workflow " + workflow.getWorkflowArchive.getWorkflowName + " is already registered.")
      val nodes = workflow.getDag.getNodes
      val futures = new util.ArrayList[Future[ModelRegistrationResult]]
//      import scala.collection.JavaConversions._
      for (entry <- nodes.entrySet.asScala) {
        val node = entry.getValue
        val wfm = node.getWorkflowModel
        futures.add(executorCompletionService.submit(() => registerModelWrapper(wfm, responseTimeout, startupTimeout, synchronous)))
      }
      var i = 0
      while (i < futures.size) {
        i += 1
        val future = executorCompletionService.take
        val result = future.get

        if (result.response.getHttpResponseCode != HttpURLConnection.HTTP_OK) {
          failed = true
          var msg: String = null
          if (result.response.getStatus == null) msg = "Failed to register the model " + result.modelName + ". Check error logs."
          else msg = result.response.getStatus
          failedMessages.add(msg)
        }
        else successNodes.add(result.modelName)
      }
      if (failed) {
        var rollbackFailure: String = null
        try removeArtifacts(workflowName, workflow, successNodes)
        catch {
          case e: Exception =>
            rollbackFailure = "Error while doing rollback of failed workflow. Details" + e.getMessage
        }
        if (rollbackFailure != null) failedMessages.add(rollbackFailure)
        status.setHttpResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
        val message = String.format("Workflow %s has failed to register. Failures: %s", workflow.getWorkflowArchive.getWorkflowName, failedMessages.toString)
        status.setStatus(message)
        status.setE(new WorkflowException(message))
      }
      else {
        status.setHttpResponseCode(HttpURLConnection.HTTP_OK)
        status.setStatus(String.format("Workflow %s has been registered and scaled successfully.", workflow.getWorkflowArchive.getWorkflowName))
        workflowMap.putIfAbsent(workflow.getWorkflowArchive.getWorkflowName, workflow)
      }
    } catch {
      case e: DownloadArchiveException =>
        status.setHttpResponseCode(HttpURLConnection.HTTP_BAD_REQUEST)
        status.setStatus("Failed to download workflow archive file")
        status.setE(e)
      case e: InvalidDAGException =>
        status.setHttpResponseCode(HttpURLConnection.HTTP_BAD_REQUEST)
        status.setStatus("Invalid workflow specification")
        status.setE(e)
      case e@(_: InterruptedException | _: ExecutionException | _: IOException) =>
        status.setHttpResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
        status.setStatus("Failed to register workflow.")
        status.setE(e)
    } finally executorService.shutdown()
    status
  }

  def registerModelWrapper(wfm: WorkflowModel, responseTimeout: Int, startupTimeout: Int, synchronous: Boolean): ModelRegistrationResult = {
    var status = new StatusResponse
    try status = ApiUtils.handleRegister(wfm.url, wfm.name, null, wfm.handler, wfm.batchSize, wfm.maxBatchDelay, responseTimeout, startupTimeout, wfm.maxWorkers, synchronous, true, false)
    catch {
      case e: Exception =>
        status.setHttpResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
        var msg: String = null
        if (e.getMessage == null) msg = "Check error logs."
        else msg = e.getMessage
        status.setStatus(String.format("Workflow Node %s failed to register. Details: %s", wfm.name, msg))
        status.setE(e)
        WorkflowManager.logger.error("Model '" + wfm.name + "' failed to register.", e)
    }
    new ModelRegistrationResult(wfm.name, status)
  }

  def getWorkflows: ConcurrentHashMap[String, WorkFlow] = workflowMap

  @throws[WorkflowNotFoundException]
  @throws[InterruptedException]
  @throws[ExecutionException]
  def unregisterWorkflow(workflowName: String, successNodes: util.ArrayList[String]): Unit = {
    val workflow = workflowMap.get(workflowName)
    if (workflow == null) throw new WorkflowNotFoundException("Workflow not found: " + workflowName)
    workflowMap.remove(workflowName)
    removeArtifacts(workflowName, workflow, successNodes)
  }

  @throws[ExecutionException]
  @throws[InterruptedException]
  def removeArtifacts(workflowName: String, workflow: WorkFlow, successNodes: util.ArrayList[String]): Unit = {
    WorkflowArchive.removeWorkflow(configManager.getWorkflowStore, workflow.getWorkflowArchive.getUrl)
    val nodes = workflow.getDag.getNodes
    unregisterModels(workflowName, nodes, successNodes)
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  def unregisterModels(workflowName: String, nodes: util.Map[String, Node], successNodes: util.ArrayList[String]): Unit = {
    val executorService = Executors.newFixedThreadPool(4)
    val executorCompletionService = new ExecutorCompletionService[ModelRegistrationResult](executorService)
    val futures = new util.ArrayList[Future[ModelRegistrationResult]]
//    import scala.collection.JavaConversions._
    for (entry <- nodes.entrySet.asScala) {
      val node = entry.getValue
      val wfm = node.getWorkflowModel
      futures.add(executorCompletionService.submit(() => {
        val status = new StatusResponse
        try {
          ApiUtils.unregisterModel(wfm.name, null)
          status.setHttpResponseCode(HttpURLConnection.HTTP_OK)
          status.setStatus(String.format("Unregisterd workflow node %s", wfm.name))
        } catch {
          case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
            if (successNodes == null || successNodes.contains(wfm.name)) {
              status.setHttpResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
              status.setStatus(String.format("Error while unregistering workflow node %s", wfm.name))
              status.setE(e)
              WorkflowManager.logger.error("Model '" + wfm.name + "' failed to unregister.", e)
            }
            else {
              status.setHttpResponseCode(HttpURLConnection.HTTP_OK)
              status.setStatus(String.format("Error while unregistering workflow node %s but can be ignored.", wfm.name))
              status.setE(e)
            }
          case e: Exception =>
            status.setHttpResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
            status.setStatus(String.format("Error while unregistering workflow node %s", wfm.name))
            status.setE(e)
        }
        new ModelRegistrationResult(wfm.name, status)
      }))
    }
    var i = 0
    var failed = false
    val failedMessages = new util.ArrayList[String]
    while (i < futures.size) {
      i += 1
      val future = executorCompletionService.take
      val result = future.get
      if (result.response.getHttpResponseCode != HttpURLConnection.HTTP_OK) {
        failed = true
        failedMessages.add(result.response.getStatus)
      }
    }
    if (failed) throw new InternalServerException("Error while unregistering the workflow " + workflowName + ". Details: " + failedMessages.toArray.toString)
    executorService.shutdown()
  }

  def getWorkflow(workflowName: String): WorkFlow = workflowMap.get(workflowName)

  @throws[WorkflowNotFoundException]
  def predict(ctx: ChannelHandlerContext, wfName: String, input: RequestInput): Unit = {
    val wf = workflowMap.get(wfName)
    if (wf != null) {
      val dagExecutor = new DagExecutor(wf.getDag)
      val predictionFuture = CompletableFuture.supplyAsync(() => dagExecutor.execute(input, null))
      predictionFuture.thenApplyAsync((predictions: util.ArrayList[NodeOutput]) => {
        if (!predictions.isEmpty) if (predictions.size == 1) {
          val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, true)
          resp.headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
          resp.content.writeBytes(predictions.get(0).getData.asInstanceOf[Array[Byte]])
          NettyUtils.sendHttpResponse(ctx, resp, true)
        }
        else {
          val result = new JsonObject
//          import scala.collection.JavaConversions._
          for (prediction <- predictions.asScala) {
            val `val` = new String(prediction.getData.asInstanceOf[Array[Byte]], StandardCharsets.UTF_8)
            result.add(prediction.getNodeName, JsonParser.parseString(`val`).getAsJsonObject)
          }
          NettyUtils.sendJsonResponse(ctx, result)
        }
        else throw new InternalServerException("Workflow inference request failed!")
        null
      }, inferenceExecutorService).exceptionally((ex: Throwable) => {
        val error = ex.getMessage.split(":")
        NettyUtils.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, new InternalServerException(error(error.length - 1).strip))
        null
      })
    }
    else throw new WorkflowNotFoundException("Workflow not found: " + wfName)
  }
}
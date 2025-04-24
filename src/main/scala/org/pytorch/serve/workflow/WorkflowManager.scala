package org.pytorch.serve.workflow

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.{JsonObject, JsonParser}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.{ModelNotFoundException, ModelVersionNotFoundException}
import org.pytorch.serve.archive.workflow.{InvalidWorkflowException, WorkflowArchive, WorkflowException, WorkflowNotFoundException}
import org.pytorch.serve.ensemble.*
import org.pytorch.serve.http.{BadRequestException, ConflictStatusException, InternalServerException, StatusResponse}
import org.pytorch.serve.util.messages.RequestInput
import org.pytorch.serve.util.{ApiUtils, ConfigManager, NettyUtils}
import org.pytorch.serve.workflow.messages.ModelRegistrationResult
import org.slf4j.{Logger, LoggerFactory}

import java.io.IOException
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util
import java.util.concurrent.*
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*


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
  //  final private var workflowMap: ConcurrentHashMap[String, WorkFlow] = new ConcurrentHashMap[String, WorkFlow]
  final private var workflowMap: TrieMap[String, WorkFlow] = new TrieMap[String, WorkFlow]
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
    val failedMessages = new ListBuffer[String]
    val successNodes = new ListBuffer[String]
    try {
      val archive = createWorkflowArchive(workflowName, url)
      val workflow = createWorkflow(archive)
      if (workflowMap.get(workflow.getWorkflowArchive.getWorkflowName) != null) throw new ConflictStatusException("Workflow " + workflow.getWorkflowArchive.getWorkflowName + " is already registered.")
      val nodes = workflow.getDag.getNodes
      val futures = new ListBuffer[Future[ModelRegistrationResult]]
//      import scala.collection.JavaConversions._
      for (entry <- nodes.toSeq) {
        val node = entry._2 //.getValue
        val wfm = node.getWorkflowModel
        futures.append(executorCompletionService.submit(() => registerModelWrapper(wfm, responseTimeout, startupTimeout, synchronous)))
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
          failedMessages.append(msg)
        }
        else successNodes.append(result.modelName)
      }
      if (failed) {
        var rollbackFailure: String = null
        try removeArtifacts(workflowName, workflow, successNodes.toList)
        catch {
          case e: Exception =>
            rollbackFailure = "Error while doing rollback of failed workflow. Details" + e.getMessage
        }
        if (rollbackFailure != null) failedMessages.append(rollbackFailure)
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

  @throws[ExecutionException]
  @throws[InterruptedException]
  def removeArtifacts(workflowName: String, workflow: WorkFlow, successNodes: List[String]): Unit = {
    WorkflowArchive.removeWorkflow(configManager.getWorkflowStore, workflow.getWorkflowArchive.getUrl)
    val nodes = workflow.getDag.getNodes
    unregisterModels(workflowName, nodes, successNodes)
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  def unregisterModels(workflowName: String, nodes: Map[String, Node], successNodes: List[String]): Unit = {
    val executorService = Executors.newFixedThreadPool(4)
    val executorCompletionService = new ExecutorCompletionService[ModelRegistrationResult](executorService)
    val futures = new ListBuffer[Future[ModelRegistrationResult]]
//    import scala.collection.JavaConversions._
    for (entry <- nodes.toSeq) {
      val node = entry._2 //.getValue
      val wfm = node.getWorkflowModel
      futures.append(executorCompletionService.submit(() => {
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
    val failedMessages = new ListBuffer[String]
    while (i < futures.size) {
      i += 1
      val future = executorCompletionService.take
      val result = future.get
      if (result.response.getHttpResponseCode != HttpURLConnection.HTTP_OK) {
        failed = true
        failedMessages.append(result.response.getStatus)
      }
    }
    if (failed) throw new InternalServerException("Error while unregistering the workflow " + workflowName + ". Details: " + failedMessages.toArray.toString)
    executorService.shutdown()
  }

  def getWorkflows: TrieMap[String, WorkFlow] = workflowMap

  //  def getWorkflows: ConcurrentHashMap[String, WorkFlow] = workflowMap
  @throws[WorkflowNotFoundException]
  @throws[InterruptedException]
  @throws[ExecutionException]
  def unregisterWorkflow(workflowName: String, successNodes: List[String]): Unit = {
    val workflow = workflowMap.get(workflowName)
    if (workflow == null) throw new WorkflowNotFoundException("Workflow not found: " + workflowName)
    workflowMap.remove(workflowName)
    removeArtifacts(workflowName, workflow.get, successNodes)
  }

  def getWorkflow(workflowName: String): WorkFlow = workflowMap.get(workflowName).get

  @throws[WorkflowNotFoundException]
  def predict(ctx: ChannelHandlerContext, wfName: String, input: RequestInput): Unit = {
    val wf = workflowMap.get(wfName)
    if (wf != null) {
      val dagExecutor = new DagExecutor(wf.get.getDag)
      val predictionFuture = CompletableFuture.supplyAsync(() => dagExecutor.execute(input, null))
      predictionFuture.thenApplyAsync((predictions: List[NodeOutput]) => {
        if (!predictions.isEmpty) if (predictions.size == 1) {
          val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, true)
          resp.headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
          resp.content.writeBytes(predictions(0).getData.asInstanceOf[Array[Byte]])
          NettyUtils.sendHttpResponse(ctx, resp, true)
        }
        else {
          val result = new JsonObject
          for (prediction <- predictions) {
            val `val` = new String(prediction.getData.asInstanceOf[Array[Byte]], StandardCharsets.UTF_8)
            result.add(prediction.getNodeName, JsonParser.parseString(`val`).getAsJsonObject)
          }
          NettyUtils.sendJsonResponse(ctx, result)
        }
        else throw new InternalServerException("Workflow inference request failed!")
        null
      }, inferenceExecutorService).exceptionally((ex: Throwable) => {
        val error = ex.getMessage.split(":")
        NettyUtils.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, new InternalServerException(error(error.length - 1).trim))
        null
      })
    }
    else throw new WorkflowNotFoundException("Workflow not found: " + wfName)
  }
}
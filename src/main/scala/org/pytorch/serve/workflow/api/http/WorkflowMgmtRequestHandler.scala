package org.pytorch.serve.workflow.api.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.workflow.{WorkflowException, WorkflowNotFoundException}
import org.pytorch.serve.ensemble.WorkFlow
import org.pytorch.serve.http.*
import org.pytorch.serve.util.{JsonUtils, NettyUtils}
import org.pytorch.serve.wlm.WorkerInitializationException
import org.pytorch.serve.workflow.WorkflowManager
import org.pytorch.serve.workflow.messages.{DescribeWorkflowResponse, ListWorkflowResponse, RegisterWorkflowRequest}

import java.net.HttpURLConnection
import java.util
import java.util.Collections
import java.util.concurrent.ExecutionException
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/**
 * A class handling inbound HTTP requests to the workflow management API.
 *
 * <p>This class
 */
object WorkflowMgmtRequestHandler {
  private def createWorkflowResponse(workflowName: String, workflow: WorkFlow) = {
    val response = DescribeWorkflowResponse(workflowName = workflowName ,workflowUrl =workflow.getWorkflowArchive.getUrl ,minWorkers =workflow.getMinWorkers ,maxWorkers = workflow.getMaxWorkers ,batchSize = workflow.getBatchSize ,maxBatchDelay =workflow.getMaxBatchDelay ,workflowDag = workflow.getWorkflowDag)
//    response.setWorkflowName(workflowName)
//    response.setWorkflowUrl(workflow.getWorkflowArchive.getUrl)
//    response.setBatchSize(workflow.getBatchSize)
//    response.setMaxBatchDelay(workflow.getMaxBatchDelay)
//    response.setMaxWorkers(workflow.getMaxWorkers)
//    response.setMinWorkers(workflow.getMinWorkers)
//    response.setWorkflowDag(workflow.getWorkflowDag)
    response
  }
}

class WorkflowMgmtRequestHandler extends HttpRequestHandlerChain {
  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    if (isManagementReq(segments)) {
      if (!("workflows" == segments(1))) throw new ResourceNotFoundException
      val method = req.method
      if (segments.length < 3) {
        if (HttpMethod.GET == method) {
          handleListWorkflows(ctx, decoder)
          return
        }
        else if (HttpMethod.POST == method) {
          handleRegisterWorkflows(ctx, decoder, req)
          return
        }
        throw new MethodNotAllowedException
      }
      if (HttpMethod.GET == method) handleDescribeWorkflow(ctx, segments(2))
      else if (HttpMethod.DELETE == method) handleUnregisterWorkflow(ctx, segments(2))
      else throw new MethodNotAllowedException
    }
    else chain.handleRequest(ctx, req, decoder, segments)
  }

  private def isManagementReq(segments: Array[String]) = segments.length == 0 || ((segments.length >= 2 && segments.length <= 4) && segments(1) == "workflows")

  private def handleListWorkflows(ctx: ChannelHandlerContext, decoder: QueryStringDecoder): Unit = {
    var limit = NettyUtils.getIntParameter(decoder, "limit", 100)
    var pageToken = NettyUtils.getIntParameter(decoder, "next_page_token", 0)
    if (limit > 100 || limit < 0) limit = 100
    if (pageToken < 0) pageToken = 0
    val workflows = WorkflowManager.getInstance.getWorkflows
    val keys = new ListBuffer[String]()
    keys :++ (workflows.keySet)
    keys.sorted
    //    Collections.sort(keys)
    val list = new ListWorkflowResponse
    var last = pageToken + limit
    if (last > keys.size) last = keys.size
    else list.setNextPageToken(String.valueOf(last))
    for (i <- pageToken until last) {
      val workflowName = keys(i)
      val workFlow = workflows.get(workflowName).get
      list.addModel(workflowName, workFlow.getWorkflowArchive.getUrl)
    }
    NettyUtils.sendJsonResponse(ctx, list)
  }

  @throws[WorkflowNotFoundException]
  private def handleDescribeWorkflow(ctx: ChannelHandlerContext, workflowName: String): Unit = {
    val resp = new ListBuffer[DescribeWorkflowResponse]
    val workFlow = WorkflowManager.getInstance.getWorkflow(workflowName)
    if (workFlow == null) throw new WorkflowNotFoundException("Workflow not found: " + workflowName)
    resp.append(WorkflowMgmtRequestHandler.createWorkflowResponse(workflowName, workFlow))
    NettyUtils.sendJsonResponse(ctx, resp)
  }

  @throws[ConflictStatusException]
  @throws[WorkflowException]
  private def handleRegisterWorkflows(ctx: ChannelHandlerContext, decoder: QueryStringDecoder, req: FullHttpRequest): Unit = {
    val registerWFRequest = parseRequest(req, decoder)
    val status = WorkflowManager.getInstance.registerWorkflow(registerWFRequest.getWorkflowName, registerWFRequest.getWorkflowUrl, registerWFRequest.getResponseTimeout, registerWFRequest.getStartupTimeout, true, registerWFRequest.getS3SseKms)
    sendResponse(ctx, status)
  }

  @throws[WorkflowNotFoundException]
  private def handleUnregisterWorkflow(ctx: ChannelHandlerContext, workflowName: String): Unit = {
    var statusResponse: StatusResponse = null
    try {
      WorkflowManager.getInstance.unregisterWorkflow(workflowName, null)
      val msg = "Workflow \"" + workflowName + "\" unregistered"
      statusResponse = new StatusResponse(msg, HttpResponseStatus.OK.code)
    } catch {
      case e@(_: InterruptedException | _: ExecutionException) =>
        val msg = "Error while unregistering the workflow " + workflowName + ". Workflow not found."
        statusResponse = new StatusResponse(msg, HttpResponseStatus.INTERNAL_SERVER_ERROR.code)
    }
    NettyUtils.sendJsonResponse(ctx, statusResponse)
  }

  private def parseRequest(req: FullHttpRequest, decoder: QueryStringDecoder) = {
    var in: RegisterWorkflowRequest = null
    val mime = HttpUtil.getMimeType(req)
    if (HttpHeaderValues.APPLICATION_JSON.contentEqualsIgnoreCase(mime)) in = JsonUtils.GSON.fromJson(req.content.toString(CharsetUtil.UTF_8), classOf[RegisterWorkflowRequest])
    else in = new RegisterWorkflowRequest(decoder)
    in
  }

  private def sendResponse(ctx: ChannelHandlerContext, statusResponse: StatusResponse): Unit = {
    if (statusResponse != null) if (statusResponse.getHttpResponseCode >= HttpURLConnection.HTTP_OK && statusResponse.getHttpResponseCode < HttpURLConnection.HTTP_MULT_CHOICE) NettyUtils.sendJsonResponse(ctx, statusResponse)
    else {
      // Re-map HTTPURLConnections HTTP_ENTITY_TOO_LARGE to Netty's INSUFFICIENT_STORAGE
      val httpResponseStatus = statusResponse.getHttpResponseCode
      NettyUtils.sendError(ctx, HttpResponseStatus.valueOf(if (httpResponseStatus == HttpURLConnection.HTTP_ENTITY_TOO_LARGE) 507
      else httpResponseStatus), statusResponse.getE)
    }
  }
}
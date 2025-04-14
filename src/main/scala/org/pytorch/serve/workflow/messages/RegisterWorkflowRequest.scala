package org.pytorch.serve.workflow.messages

import com.google.gson.annotations.SerializedName
import io.netty.handler.codec.http.QueryStringDecoder
import org.pytorch.serve.util.NettyUtils

class RegisterWorkflowRequest(decoder: QueryStringDecoder) {
  
  @SerializedName("workflow_name") 
  private var workflowName = NettyUtils.getParameter(decoder, "workflow_name", null)
  @SerializedName("response_timeout") 
  private var responseTimeout =  NettyUtils.getIntParameter(decoder, "response_timeout", 120)
  @SerializedName("startup_timeout") 
  private var startupTimeout = NettyUtils.getIntParameter(decoder, "startup_timeout", 120)
  @SerializedName("url") 
  private var workflowUrl = NettyUtils.getParameter(decoder, "url", null)
  @SerializedName("s3_sse_kms") 
  private var s3SseKms:Boolean = NettyUtils.getParameter(decoder, "s3_sse_kms", "false").asInstanceOf[Boolean]

  def setWorkflowName(workflowName: String): Unit = {
    this.workflowName = workflowName
  }

  def getWorkflowName: String = workflowName

  def getResponseTimeout: Int = responseTimeout

  def setResponseTimeout(responseTimeout: Int): Unit = {
    this.responseTimeout = responseTimeout
  }

  def getStartupTimeout: Int = startupTimeout

  def setStartupTimeout(startupTimeout: Int): Unit = {
    this.startupTimeout = startupTimeout
  }

  def getWorkflowUrl: String = workflowUrl

  def setWorkflowUrl(workflowUrl: String): Unit = {
    this.workflowUrl = workflowUrl
  }

  def getS3SseKms: Boolean = s3SseKms
}
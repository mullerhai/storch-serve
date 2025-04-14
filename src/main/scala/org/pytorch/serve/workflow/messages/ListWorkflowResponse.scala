package org.pytorch.serve.workflow.messages

import java.util
import scala.jdk.CollectionConverters._

object ListWorkflowResponse {
  class WorkFlowItem( var workflowName: String,  var workflowUrl: String) {
    def getWorkflowName: String = workflowName

    def setWorkflowName(workflowName: String): Unit = {
      this.workflowName = workflowName
    }

    def getWorkflowUrl: String = workflowUrl

    def setWorkflowUrl(workflowUrl: String): Unit = {
      this.workflowUrl = workflowUrl
    }
  }
}

class ListWorkflowResponse {
  
  private var nextPageToken: String = null
  private var workflows: util.List[ListWorkflowResponse.WorkFlowItem] = new util.ArrayList[ListWorkflowResponse.WorkFlowItem]

  def getNextPageToken: String = nextPageToken

  def setNextPageToken(nextPageToken: String): Unit = {
    this.nextPageToken = nextPageToken
  }

  def getWorkflows: util.List[ListWorkflowResponse.WorkFlowItem] = workflows

  def setWorkflows(workflows: util.List[ListWorkflowResponse.WorkFlowItem]): Unit = {
    this.workflows = workflows
  }

  def addModel(workflowName: String, workflowUrl: String): Unit = {
    workflows.add(new ListWorkflowResponse.WorkFlowItem(workflowName, workflowUrl))
  }
}
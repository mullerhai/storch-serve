package org.pytorch.serve.workflow.messages

import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

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
  private var workflows: ListBuffer[ListWorkflowResponse.WorkFlowItem] = new ListBuffer[ListWorkflowResponse.WorkFlowItem]

  def getNextPageToken: String = nextPageToken

  def setNextPageToken(nextPageToken: String): Unit = {
    this.nextPageToken = nextPageToken
  }

  def getWorkflows: List[ListWorkflowResponse.WorkFlowItem] = workflows.toList

  def setWorkflows(workflows: List[ListWorkflowResponse.WorkFlowItem]): Unit = {
    this.workflows.addAll(workflows)
  }

  def addModel(workflowName: String, workflowUrl: String): Unit = {
    workflows.append(new ListWorkflowResponse.WorkFlowItem(workflowName, workflowUrl))
  }
}
package org.pytorch.serve.ensemble

class Node( var name: String,var workflowModel: WorkflowModel) {
  private var parentName: String = null

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }

  def getParentName: String = parentName

  def setParentName(parentName: String): Unit = {
    this.parentName = parentName
  }

  def getWorkflowModel: WorkflowModel = workflowModel
}
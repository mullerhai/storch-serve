package org.pytorch.serve.archive.workflow

object Manifest {
  final class Workflow {
    private var workflowName: String = null
    private var specFile: String = null
    private var handler: String = null

    def getWorkflowName: String = workflowName

    def setWorkflowName(workflowName: String): Unit = {
      this.workflowName = workflowName
    }

    def getSpecFile: String = specFile

    def setSpecFile(specFile: String): Unit = {
      this.specFile = specFile
    }

    def getHandler: String = handler

    def setHandler(handler: String): Unit = {
      this.handler = handler
    }
  }
}

class Manifest {

  private var createdOn: String = null
  private var description: String = null
  private var archiverVersion: String = null
  private var workflow: Manifest.Workflow = new Manifest.Workflow

  def getCreatedOn: String = createdOn

  def setCreatedOn(createdOn: String): Unit = {
    this.createdOn = createdOn
  }

  def getDescription: String = description

  def setDescription(description: String): Unit = {
    this.description = description
  }

  def getArchiverVersion: String = archiverVersion

  def setArchiverVersion(archiverVersion: String): Unit = {
    this.archiverVersion = archiverVersion
  }

  def getWorkflow: Manifest.Workflow = workflow

  def setWorkflow(workflow: Manifest.Workflow): Unit = {
    this.workflow = workflow
  }
}
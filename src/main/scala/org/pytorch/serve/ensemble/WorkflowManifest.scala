package org.pytorch.serve.ensemble

import com.google.gson.annotations.SerializedName
import scala.jdk.CollectionConverters._
object WorkflowManifest {
  final class Workflow {
    private var workflowName: String = null
    private var version: String = null
    private var specFile: String = null
    private var workflowVersion: String = null
    private var handler: String = null
    private var requirementsFile: String = null

    def getWorkflowName: String = workflowName

    def setWorkflowName(workflowName: String): Unit = {
      this.workflowName = workflowName
    }

    def getVersion: String = version

    def setVersion(version: String): Unit = {
      this.version = version
    }

    def getModelVersion: String = workflowVersion

    def setModelVersion(modelVersion: String): Unit = {
      this.workflowVersion = modelVersion
    }

    def getRequirementsFile: String = requirementsFile

    def setRequirementsFile(requirementsFile: String): Unit = {
      this.requirementsFile = requirementsFile
    }

    def getHandler: String = handler

    def setHandler(handler: String): Unit = {
      this.handler = handler
    }

    def getSpecFile: String = specFile

    def setSpecFile(specFile: String): Unit = {
      this.specFile = specFile
    }
  }

  enum RuntimeType:
    case PYTHON, PYTHON3, LSP, SCALA
//  object RuntimeType extends Enumeration {
//    type RuntimeType = Value
//    val PYTHON, PYTHON3, LSP = Value
//    private[ensemble] var value = nulldef
//    this (value: String) {
//      this ()
//      this.value = value
//    }
//
//    def getValue: String = value
//
//    def fromValue(value: String): WorkflowManifest.RuntimeType = {
//      for (runtime <- values) {
//        if (runtime.value == value) return runtime
//      }
//      throw new IllegalArgumentException("Invalid RuntimeType value: " + value)
//    }
//  }
  
}

class WorkflowManifest {

  private var createdOn: String = null
  private var description: String = null
  private var archiverVersion: String = null
  private var runtime: WorkflowManifest.RuntimeType = WorkflowManifest.RuntimeType.PYTHON
  private var workflow: WorkflowManifest.Workflow = new WorkflowManifest.Workflow

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

  def getRuntime: WorkflowManifest.RuntimeType = runtime

  def setRuntime(runtime: WorkflowManifest.RuntimeType): Unit = {
    this.runtime = runtime
  }

  def getWorfklow: WorkflowManifest.Workflow = workflow

  def setModel(model: WorkflowManifest.Workflow): Unit = {
    this.workflow = workflow
  }
}
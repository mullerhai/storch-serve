package org.pytorch.serve.archive.model

import com.google.gson.annotations.SerializedName

object Manifest {
  final class Model {
    private var modelName: String = null
    private var version: String = null
    private var description: String = null
    private var modelVersion: String = null
    private var handler: String = null
    private var envelope: String = null
    private var requirementsFile: String = null
    private var configFile: String = null

    def getModelName: String = modelName

    def setModelName(modelName: String): Unit = {
      this.modelName = modelName
    }

    def getVersion: String = version

    def setVersion(version: String): Unit = {
      this.version = version
    }

    def getDescription: String = description

    def setDescription(description: String): Unit = {
      this.description = description
    }

    def getModelVersion: String = modelVersion

    def setModelVersion(modelVersion: String): Unit = {
      this.modelVersion = modelVersion
    }

    def getRequirementsFile: String = requirementsFile

    def setRequirementsFile(requirementsFile: String): Unit = {
      this.requirementsFile = requirementsFile
    }

    def getHandler: String = handler

    def setHandler(handler: String): Unit = {
      this.handler = handler
    }

    def getEnvelope: String = envelope

    def setEnvelope(envelope: String): Unit = {
      this.envelope = envelope
    }

    def getConfigFile: String = configFile

    def setConfigFile(configFile: String): Unit = {
      this.configFile = configFile
    }
  }

  enum RuntimeType:
    case PYTHON, PYTHON3, LSP, SCALA
//  object RuntimeType extends Enumeration {
//    type RuntimeType = Value
//    val PYTHON, PYTHON3, LSP = Value
//    private[model] var value = null
//    def this (value: String) {
//      this ()
//      this.value = value
//    }
//
//    def getValue: String = value
//
//    def fromValue(value: String): Manifest.RuntimeType = {
//      for (runtime <- values) {
//        if (runtime.value == value) return runtime
//      }
//      throw new IllegalArgumentException("Invalid RuntimeType value: " + value)
//    }
//  }
}

class Manifest {
//  runtime = Manifest.RuntimeType.PYTHON
//  model = new Manifest.Model
  private var createdOn: String = null
  private var description: String = null
  private var archiverVersion: String = null
  private var runtime: Manifest.RuntimeType = Manifest.RuntimeType.PYTHON
  private var model: Manifest.Model = new Manifest.Model

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

  def getRuntime: Manifest.RuntimeType = runtime

  def setRuntime(runtime: Manifest.RuntimeType): Unit = {
    this.runtime = runtime
  }

  def getModel: Manifest.Model = model

  def setModel(model: Manifest.Model): Unit = {
    this.model = model
  }
}
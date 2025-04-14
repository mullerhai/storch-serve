package org.pytorch.serve.util.messages

import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.wlm.Model
import scala.jdk.CollectionConverters._
class ModelLoadModelRequest(model: Model, private var gpuId: Int) extends BaseModelRequest(WorkerCommands.LOAD, model.getModelName) {
  private var modelPath = model.getModelDir.getAbsolutePath
  private var handler = model.getModelArchive.getManifest.getModel.getHandler
  private var envelope = model.getModelArchive.getManifest.getModel.getEnvelope
  private var batchSize = model.getBatchSize
  private var limitMaxImagePixels = ConfigManager.getInstance.isLimitMaxImagePixels
  /**
   * ModelLoadModelRequest is a interface between frontend and backend to notify the backend to
   * load a particular model.
   */


  def getModelPath: String = modelPath

  def getHandler: String = handler

  def getEnvelope: String = envelope

  def getBatchSize: Int = batchSize

  def getGpuId: Int = gpuId

  def isLimitMaxImagePixels: Boolean = limitMaxImagePixels
}
package org.pytorch.serve.device

import java.text.MessageFormat
import org.pytorch.serve.device.interfaces.IAcceleratorUtility

class Accelerator(val model: String, val vendor: AcceleratorVendor, val id: Integer) {
  var acceleratorUtility: IAcceleratorUtility = null
  var usagePercentage = .0f
  var memoryUtilizationPercentage = .0f
  var memoryAvailableMegabytes: Integer = null
  var memoryUtilizationMegabytes: Integer = null

  // Getters
  def getMemoryAvailableMegaBytes: Integer = memoryAvailableMegabytes

  def getVendor: AcceleratorVendor = vendor

  def getAcceleratorModel: String = model

  def getAcceleratorId: Integer = id

  def getUsagePercentage: Float = usagePercentage

  def getMemoryUtilizationPercentage: Float = memoryUtilizationPercentage

  def getMemoryUtilizationMegabytes: Integer = memoryUtilizationMegabytes

  // Setters
  def setMemoryAvailableMegaBytes(memoryAvailable: Integer): Unit = {
    this.memoryAvailableMegabytes = memoryAvailable
  }

  def setUsagePercentage(acceleratorUtilization: Float): Unit = {
    this.usagePercentage = acceleratorUtilization
  }

  def setMemoryUtilizationPercentage(memoryUtilizationPercentage: Float): Unit = {
    this.memoryUtilizationPercentage = memoryUtilizationPercentage
  }

  def setMemoryUtilizationMegabytes(memoryUtilizationMegabytes: Integer): Unit = {
    this.memoryUtilizationMegabytes = memoryUtilizationMegabytes
  }

  // Other Methods
  def utilizationToString: String = {
    val message = MessageFormat.format("gpuId::{0} utilization.gpu::{1} % utilization.memory::{2} % memory.used::{3} MiB", id, usagePercentage, memoryUtilizationPercentage, memoryUtilizationMegabytes)
    message
  }

  def updateDynamicAttributes(updated: Accelerator): Unit = {
    this.usagePercentage = updated.usagePercentage
    this.memoryUtilizationPercentage = updated.memoryUtilizationPercentage
    this.memoryUtilizationMegabytes = updated.memoryUtilizationMegabytes
  }
}
package org.pytorch.serve.device.utils

import org.pytorch.serve.device.interfaces.{IAcceleratorUtility, ICsvSmiParser}
import org.pytorch.serve.device.{Accelerator, AcceleratorVendor}

import java.util
import scala.collection.mutable

class CudaUtil extends IAcceleratorUtility with ICsvSmiParser {
  override def getGpuEnvVariableName = "CUDA_VISIBLE_DEVICES"

  override def getUtilizationSmiCommand: Array[String] = {
    val metrics = String.join(",", "index", "gpu_name", "utilization.gpu", "utilization.memory", "memory.used")
    Array[String]("nvidia-smi", "--query-gpu=" + metrics, "--format=csv,nounits")
  }

  override def getAvailableAccelerators(availableAcceleratorIds: mutable.LinkedHashSet[Integer]): List[Accelerator] = {
    val command = Array("nvidia-smi", "--query-gpu=index,gpu_name", "--format=csv,nounits")
    val smiOutput = IAcceleratorUtility.callSMI(command)
    csvSmiOutputToAccelerators(smiOutput, availableAcceleratorIds, this.parseAccelerator)
  }

  override def smiOutputToUpdatedAccelerators(smiOutput: String, parsedGpuIds: mutable.LinkedHashSet[Integer]): List[Accelerator] = csvSmiOutputToAccelerators(smiOutput, parsedGpuIds, this.parseUpdatedAccelerator)

  def parseAccelerator(parts: Array[String]): Accelerator = {
    val id = parts(0).trim.toInt
    val model = parts(1).trim
    new Accelerator(model, AcceleratorVendor.NVIDIA, id)
  }

  def parseUpdatedAccelerator(parts: Array[String]): Accelerator = {
    val id = parts(0).trim.toInt
    val model = parts(1).trim
    val usagePercentage = parts(2).trim.toFloat
    val memoryUtilizationPercentage = parts(3).trim.toFloat
    val memoryUtilizationMegabytes = parts(4).trim.toInt
    val accelerator = new Accelerator(model, AcceleratorVendor.NVIDIA, id)
    accelerator.setUsagePercentage(usagePercentage)
    accelerator.setMemoryUtilizationPercentage(memoryUtilizationPercentage)
    accelerator.setMemoryUtilizationMegabytes(memoryUtilizationMegabytes)
    accelerator
  }
}
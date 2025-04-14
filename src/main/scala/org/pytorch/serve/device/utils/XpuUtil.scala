package org.pytorch.serve.device.utils

import java.util
import org.pytorch.serve.device.Accelerator
import org.pytorch.serve.device.AcceleratorVendor
import org.pytorch.serve.device.interfaces.IAcceleratorUtility
import org.pytorch.serve.device.interfaces.IAcceleratorUtility.logger
import org.pytorch.serve.device.interfaces.ICsvSmiParser

class XpuUtil extends IAcceleratorUtility with ICsvSmiParser {
  override def getGpuEnvVariableName = "XPU_VISIBLE_DEVICES"

  override def getAvailableAccelerators(availableAcceleratorIds: util.LinkedHashSet[Integer]): util.ArrayList[Accelerator] = {
    val smiCommand = Array("xpu-smi", "discovery", "--dump", // output as csv
      String.join(",", "1", // device Id
        "2", // Device name
        "16" // Memory physical size
     ))
    val smiOutput = IAcceleratorUtility.callSMI(smiCommand)
    val acceleratorEnv = getGpuEnvVariableName
    val requestedAccelerators = System.getenv(acceleratorEnv)
    val parsedAcceleratorIds = IAcceleratorUtility.parseVisibleDevicesEnv(requestedAccelerators)
    csvSmiOutputToAccelerators(smiOutput, parsedAcceleratorIds, this.parseDiscoveryOutput)
  }

  override final def smiOutputToUpdatedAccelerators(smiOutput: String, parsedGpuIds: util.LinkedHashSet[Integer]): util.ArrayList[Accelerator] = csvSmiOutputToAccelerators(smiOutput, parsedGpuIds, this.parseUtilizationOutput)

  override def getUtilizationSmiCommand: Array[String] = {
    // https://intel.github.io/xpumanager/smi_user_guide.html#get-the-device-real-time-statistics
    // Timestamp, DeviceId, GPU Utilization (%), GPU Memory Utilization (%)
    // 06:14:46.000, 0, 0.00, 14.61
    // 06:14:47.000, 1, 0.00, 14.59
    val smiCommand = Array("xpu-smi", "dump", "-d -1", // all devices
      "-n 1", // one dump
      "-m", // metrics
      String.join(",", "0", // GPU Utilization (%), GPU active time of the elapsed time, per tile or
        // device.
        // Device-level is the average value of tiles for multi-tiles.
        "5" // GPU Memory Utilization (%), per tile or device. Device-level is the
           ))
    smiCommand
  }

  private def parseDiscoveryOutput(parts: Array[String]) = {
    val acceleratorId = parts(1).trim.toInt
    val deviceName = parts(2).trim
    logger.debug("Found accelerator at index: {}, Card name: {}", acceleratorId, deviceName)
    new Accelerator(deviceName, AcceleratorVendor.INTEL, acceleratorId)
  }

  private def parseUtilizationOutput(parts: Array[String]) = {
    val acceleratorId = parts(1).trim.toInt
    val usagePercentage = parts(2).toFloat
    val memoryUsagePercentage = parts(3).toFloat
    val accelerator = new Accelerator("", AcceleratorVendor.INTEL, acceleratorId)
    accelerator.setUsagePercentage(usagePercentage)
    accelerator.setMemoryUtilizationPercentage(memoryUsagePercentage)
    accelerator
  }
}
package org.pytorch.serve.device

import java.io.IOException
import java.util
import java.util.Optional
import java.util.stream.Collectors
import org.pytorch.serve.device.interfaces.IAcceleratorUtility
import org.pytorch.serve.device.utils._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
object SystemInfo {
  private[device] val logger = LoggerFactory.getLogger(classOf[SystemInfo])

  def detectVendorType: AcceleratorVendor = if (isCommandAvailable("rocm-smi")) AcceleratorVendor.AMD
  else if (isCommandAvailable("nvidia-smi")) AcceleratorVendor.NVIDIA
  else if (isCommandAvailable("xpu-smi")) AcceleratorVendor.INTEL
  else if (isCommandAvailable("system_profiler")) AcceleratorVendor.APPLE
  else AcceleratorVendor.UNKNOWN

  private def isCommandAvailable(command: String) = {
    val operatingSystem = System.getProperty("os.name").toLowerCase
    val commandCheck = if (operatingSystem.contains("win")) "where"
    else "which"
    val processBuilder = new ProcessBuilder(commandCheck, command)
    try {
      val process = processBuilder.start
      val exitCode = process.waitFor
      exitCode == 0
    } catch {
      case e@(_: IOException | _: InterruptedException) =>
        false
    }
  }
}

class SystemInfo {
  // Detect and set the vendor of any accelerators in the system

  // If accelerators are present (vendor != UNKNOWN),
  // initialize accelerator utilities
  Optional.of(hasAccelerators).filter(b =>b.==(true)).ifPresent((hasAcc: Boolean) => {

    if(hasAcc){
      // Create the appropriate utility class based on vendor
      this.acceleratorUtil = createAcceleratorUtility
      // Populate the accelerators list based on environment
      // variables and available devices
      populateAccelerators()
    }

  })
  // Safely handle accelerator metrics update
  Optional.ofNullable(accelerators).filter((list: util.ArrayList[Accelerator]) => !list.isEmpty).ifPresent((list: util.ArrayList[Accelerator]) => updateAcceleratorMetrics())
  // Only proceed if hasAccelerators() returns true
  // Execute this block if accelerators are present
  // Only proceed if the accelerators list is not empty
  // Update metrics (utilization, memory, etc.) for all accelerators if list
  // exists and not empty
  //
  // Contains information about the system (physical or virtual machine)
  // we are running the workload on.
  // Specifically how many accelerators and info about them.
  //
  var acceleratorVendor: AcceleratorVendor = SystemInfo.detectVendorType
  private[device] var accelerators: util.ArrayList[Accelerator] = new util.ArrayList[Accelerator]
  private var acceleratorUtil: IAcceleratorUtility = null

  private def createAcceleratorUtility = this.acceleratorVendor match {
    case AcceleratorVendor.AMD =>
      new ROCmUtil
    case AcceleratorVendor.NVIDIA =>
      new CudaUtil
    case AcceleratorVendor.INTEL =>
      new XpuUtil
    case AcceleratorVendor.APPLE =>
      new AppleUtil
    case _ =>
      null
  }

  private def populateAccelerators(): Unit = {
    if (this.acceleratorUtil != null) {
      val envVarName = this.acceleratorUtil.getGpuEnvVariableName
      if (envVarName != null) {
        val requestedAcceleratorIds = System.getenv(envVarName)
        val availableAcceleratorIds = IAcceleratorUtility.parseVisibleDevicesEnv(requestedAcceleratorIds)
        this.accelerators = this.acceleratorUtil.getAvailableAccelerators(availableAcceleratorIds)
      }
      else {
        // Handle the case where envVarName is null
        this.accelerators = this.acceleratorUtil.getAvailableAccelerators(new util.LinkedHashSet[Integer])
      }
    }
    else this.accelerators = new util.ArrayList[Accelerator]
  }

  private[device] def hasAccelerators = this.acceleratorVendor ne AcceleratorVendor.UNKNOWN

  def getNumberOfAccelerators: Integer = {
    // since we instance create `accelerators` as an empty list
    // in the constructor, the null check should be redundant.
    // leaving it to be sure.
    if (accelerators != null) accelerators.size
    else 0
  }

  def getAccelerators: util.ArrayList[Accelerator] = this.accelerators

  private def updateAccelerators(updatedAccelerators: util.List[Accelerator]): Unit = {
    // Create a map of existing accelerators with ID as key
    val existingAcceleratorsMap = this.accelerators.stream.collect(Collectors.toMap((acc: Accelerator) => acc.id, (acc: Accelerator) => acc))
    // Update existing accelerators and add new ones 
    val arr = new util.ArrayList[Accelerator]()
    Seq(updatedAccelerators.asScala).flatten.map((updatedAcc:Accelerator) =>{
      val existingAcc = existingAcceleratorsMap.get(updatedAcc.id)
      if (existingAcc != null) {
        existingAcc.updateDynamicAttributes(updatedAcc)
        existingAcc
      }
      else updatedAcc
    }).foreach(el => arr.add(el))
    this.accelerators = arr
//    
//    this.accelerators = updatedAccelerators.stream.map((updatedAcc: Accelerator) => {
//      val existingAcc = existingAcceleratorsMap.get(updatedAcc.id)
//      if (existingAcc != null) {
//        existingAcc.updateDynamicAttributes(updatedAcc)
//        existingAcc
//      }
//      else updatedAcc
//    }).collect(Collectors.toCollection((a:Accelerator)=>arr.add(a) )) //util.ArrayList.`new`))
//  
  }

  def updateAcceleratorMetrics(): Unit = {
    if (this.acceleratorUtil != null) {
      val updatedAccelerators = this.acceleratorUtil.getUpdatedAcceleratorsUtilization(this.accelerators)
      updateAccelerators(updatedAccelerators)
    }
  }

  def getAcceleratorVendor: AcceleratorVendor = this.acceleratorVendor

  def getVisibleDevicesEnvName: String = {
    if (this.accelerators.isEmpty || this.accelerators == null) return null
    this.accelerators.get(0).acceleratorUtility.getGpuEnvVariableName
  }
}
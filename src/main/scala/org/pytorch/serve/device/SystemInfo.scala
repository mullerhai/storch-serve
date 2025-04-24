package org.pytorch.serve.device

import org.pytorch.serve.device.interfaces.IAcceleratorUtility
import org.pytorch.serve.device.utils.*
import org.slf4j.{Logger, LoggerFactory}

import java.io.IOException
import java.util
import java.util.Optional
import java.util.stream.Collectors
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
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
  Optional.ofNullable(accelerators).filter(_.nonEmpty).ifPresent(list => updateAcceleratorMetrics())
  //  Optional.ofNullable(accelerators).filter((list: List[Accelerator]) => !list.isEmpty).ifPresent((list: util.ArrayList[Accelerator]) => updateAcceleratorMetrics())
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
  private[device] var accelerators: ListBuffer[Accelerator] = new ListBuffer[Accelerator]
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

  def getAccelerators: List[Accelerator] = this.accelerators.toList

  private[device] def hasAccelerators = this.acceleratorVendor ne AcceleratorVendor.UNKNOWN

  def getNumberOfAccelerators: Integer = {
    // since we instance create `accelerators` as an empty list
    // in the constructor, the null check should be redundant.
    // leaving it to be sure.
    if (accelerators != null) accelerators.size
    else 0
  }

  def updateAcceleratorMetrics(): Unit = {
    if (this.acceleratorUtil != null) {
      val updatedAccelerators = this.acceleratorUtil.getUpdatedAcceleratorsUtilization(this.accelerators.toList)
      updateAccelerators(updatedAccelerators)
    }
  }

  private def updateAccelerators(updatedAccelerators: List[Accelerator]): Unit = {
    // Create a map of existing accelerators with ID as key
    val existingAcceleratorsMap = this.accelerators.map(acc => (acc.id, acc)).toMap //collect(Collectors.toMap((acc: Accelerator) => acc.id, (acc: Accelerator) => acc))
    // Update existing accelerators and add new ones 
    val arr = new ListBuffer[Accelerator]()
    Seq(updatedAccelerators).flatten.map((updatedAcc: Accelerator) => {
      val existingAcc = existingAcceleratorsMap.get(updatedAcc.id)
      if (existingAcc.isDefined) {
        existingAcc.get.updateDynamicAttributes(updatedAcc)
        existingAcc.get
      }
      else updatedAcc
    }).foreach(el => arr.append(el))
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

  def getVisibleDevicesEnvName: String = {
    if (this.accelerators.isEmpty || this.accelerators == null) return null
    this.accelerators(0).acceleratorUtility.getGpuEnvVariableName
  }

  def getAcceleratorVendor: AcceleratorVendor = this.acceleratorVendor

  private def populateAccelerators(): Unit = {
    if (this.acceleratorUtil != null) {
      val envVarName = this.acceleratorUtil.getGpuEnvVariableName
      if (envVarName != null) {
        val requestedAcceleratorIds = System.getenv(envVarName)
        val availableAcceleratorIds = IAcceleratorUtility.parseVisibleDevicesEnv(requestedAcceleratorIds)
        this.accelerators ++= this.acceleratorUtil.getAvailableAccelerators(availableAcceleratorIds)
      }
      else {
        // Handle the case where envVarName is null
        //        this.accelerators = this.acceleratorUtil.getAvailableAccelerators(new mutable.LinkedHashSet[Integer])
        this.accelerators ++= this.acceleratorUtil.getAvailableAccelerators(mutable.LinkedHashSet.empty)
      }
    }
    else this.accelerators = new ListBuffer[Accelerator]
  }
}
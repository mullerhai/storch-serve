package org.pytorch.serve.device.utils

import com.google.gson.{JsonArray, JsonElement, JsonObject, JsonParser}
import org.pytorch.serve.device.interfaces.{IAcceleratorUtility, IJsonSmiParser}
import org.pytorch.serve.device.{Accelerator, AcceleratorVendor}

import java.util
import java.util.Collections
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class AppleUtil extends IAcceleratorUtility with IJsonSmiParser {
  override def getGpuEnvVariableName: String = null // Apple doesn't use a GPU environment variable

  override def getUtilizationSmiCommand: Array[String] = Array[String]("system_profiler", "-json", "-detailLevel", "mini", "SPDisplaysDataType")

  override def getAvailableAccelerators(availableAcceleratorIds: mutable.LinkedHashSet[Integer]): List[Accelerator] = {
    val jsonOutput = IAcceleratorUtility.callSMI(getUtilizationSmiCommand)
    val rootObject = JsonParser.parseString(jsonOutput).getAsJsonObject
    jsonOutputToAccelerators(rootObject, availableAcceleratorIds)
  }

  override def smiOutputToUpdatedAccelerators(smiOutput: String, parsedGpuIds: mutable.LinkedHashSet[Integer]): List[Accelerator] = {
    val rootObject = JsonParser.parseString(smiOutput).getAsJsonObject
    jsonOutputToAccelerators(rootObject, parsedGpuIds)
  }

  override def jsonObjectToAccelerator(gpuObject: JsonObject): Accelerator = {
    val model = gpuObject.get("sppci_model").getAsString
    if (!model.startsWith("Apple M")) return null
    val accelerator = new Accelerator(model, AcceleratorVendor.APPLE, 0)
    // Set additional information
    accelerator.setUsagePercentage(0f) // Not available from system_profiler
    accelerator.setMemoryUtilizationPercentage(0f) // Not available from system_profiler
    accelerator.setMemoryUtilizationMegabytes(0) // Not available from system_profiler
    accelerator
  }

  override def extractAcceleratorId(cardObject: JsonObject): Integer = {
    // `system_profiler` only returns one object for
    // the integrated GPU on M1, M2, M3 Macs
    0
  }

  def jsonOutputToAccelerators(rootObject: JsonObject, parsedAcceleratorIds: mutable.LinkedHashSet[Integer]): List[Accelerator] = {
    val accelerators = new ListBuffer[Accelerator]
    val acceleratorObjects = extractAccelerators(rootObject)
    import scala.jdk.CollectionConverters.*
    for (acceleratorObject <- acceleratorObjects) {
      val acceleratorId = extractAcceleratorId(acceleratorObject)
      if (acceleratorId != null && (parsedAcceleratorIds.isEmpty || parsedAcceleratorIds.contains(acceleratorId))) {
        val accelerator = jsonObjectToAccelerator(acceleratorObject)
        accelerators.append(accelerator)
      }
    }
    accelerators.toList
  }

  override def extractAccelerators(rootObject: JsonElement): List[JsonObject] = {
    var accelerators = new ListBuffer[JsonObject]
    val displaysArray = rootObject.getAsJsonObject
      .get("SPDisplaysDataType") // Gets the "SPDisplaysDataType" element
      .getAsJsonArray() // Gets the outer object.get("SPDisplaysDataType")// Gets the "SPDisplaysDataType" element.getAsJsonArray
    val gpuObject:JsonObject = displaysArray.get(0).getAsJsonObject
    // Create list with only a single accelerator object as
    // M1, M2, M3 Macs have only single integrated GPU
    accelerators.append(gpuObject) // = Collections.singletonList(gpuObject)
    accelerators.toList
  }
}
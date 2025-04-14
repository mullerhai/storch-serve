package org.pytorch.serve.device.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util
import java.util.Collections
import org.pytorch.serve.device.Accelerator
import org.pytorch.serve.device.AcceleratorVendor
import org.pytorch.serve.device.interfaces.IAcceleratorUtility
import org.pytorch.serve.device.interfaces.IJsonSmiParser

class AppleUtil extends IAcceleratorUtility with IJsonSmiParser {
  override def getGpuEnvVariableName: String = null // Apple doesn't use a GPU environment variable

  override def getUtilizationSmiCommand: Array[String] = Array[String]("system_profiler", "-json", "-detailLevel", "mini", "SPDisplaysDataType")

  override def getAvailableAccelerators(availableAcceleratorIds: util.LinkedHashSet[Integer]): util.ArrayList[Accelerator] = {
    val jsonOutput = IAcceleratorUtility.callSMI(getUtilizationSmiCommand)
    val rootObject = JsonParser.parseString(jsonOutput).getAsJsonObject
    jsonOutputToAccelerators(rootObject, availableAcceleratorIds)
  }

  override def smiOutputToUpdatedAccelerators(smiOutput: String, parsedGpuIds: util.LinkedHashSet[Integer]): util.ArrayList[Accelerator] = {
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

  override def extractAccelerators(rootObject: JsonElement): util.ArrayList[JsonObject] = {
    var accelerators:util.ArrayList[JsonObject] = new util.ArrayList[JsonObject]
    val displaysArray = rootObject.getAsJsonObject 
      .get("SPDisplaysDataType") // Gets the "SPDisplaysDataType" element
      .getAsJsonArray() // Gets the outer object.get("SPDisplaysDataType")// Gets the "SPDisplaysDataType" element.getAsJsonArray
    val gpuObject:JsonObject = displaysArray.get(0).getAsJsonObject
    // Create list with only a single accelerator object as
    // M1, M2, M3 Macs have only single integrated GPU
    accelerators.add(gpuObject) // = Collections.singletonList(gpuObject)
    accelerators
  }

  def jsonOutputToAccelerators(rootObject: JsonObject, parsedAcceleratorIds: util.LinkedHashSet[Integer]): util.ArrayList[Accelerator] = {
    val accelerators = new util.ArrayList[Accelerator]
    val acceleratorObjects = extractAccelerators(rootObject)
    import scala.jdk.CollectionConverters._
    for (acceleratorObject <- acceleratorObjects.asScala) {
      val acceleratorId = extractAcceleratorId(acceleratorObject)
      if (acceleratorId != null && (parsedAcceleratorIds.isEmpty || parsedAcceleratorIds.contains(acceleratorId))) {
        val accelerator = jsonObjectToAccelerator(acceleratorObject)
        accelerators.add(accelerator)
      }
    }
    accelerators
  }
}
package org.pytorch.serve.device.utils

import com.google.gson.{JsonElement, JsonObject, JsonParser}
import org.pytorch.serve.device.interfaces.{IAcceleratorUtility, IJsonSmiParser}
import org.pytorch.serve.device.{Accelerator, AcceleratorVendor}

import java.util
import java.util.regex.{Matcher, Pattern}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object ROCmUtil {
  private val GPU_ID_PATTERN = Pattern.compile("card(\\d+)")
}

class ROCmUtil extends IAcceleratorUtility with IJsonSmiParser {
  override def getGpuEnvVariableName = "HIP_VISIBLE_DEVICES"

  override def getUtilizationSmiCommand: Array[String] = Array[String]("rocm-smi", "--showid", "--showproductname", "--showuse", "--showmemuse", "--showmeminfo", "vram", "-P", "--json")

  override def getAvailableAccelerators(availableAcceleratorIds: mutable.LinkedHashSet[Integer]): List[Accelerator] = {
    val smiCommand = Array("rocm-smi", "--showproductname", "-P", "--json")
    val jsonOutput = IAcceleratorUtility.callSMI(smiCommand)
    val rootObject = JsonParser.parseString(jsonOutput).getAsJsonObject
    jsonOutputToAccelerators(rootObject, availableAcceleratorIds)
  }

  override def smiOutputToUpdatedAccelerators(smiOutput: String, parsedGpuIds: mutable.LinkedHashSet[Integer]): List[Accelerator] = {
    val rootObject = JsonParser.parseString(smiOutput).getAsJsonObject
    jsonOutputToAccelerators(rootObject, parsedGpuIds)
  }

  override def extractAccelerators(rootObject: JsonElement): List[JsonObject] = {
    val root = rootObject.getAsJsonObject
    val accelerators = new ListBuffer[JsonObject]
    import scala.jdk.CollectionConverters.*
    for (key <- root.keySet.asScala) {
      if (ROCmUtil.GPU_ID_PATTERN.matcher(key).matches) {
        val accelerator = root.getAsJsonObject(key)
        accelerator.addProperty("cardId", key) // Add the card ID to the JsonObject
        accelerators.append(accelerator)
      }
    }
    accelerators.toList
  }

  override def extractAcceleratorId(jsonObject: JsonObject): Integer = {
    val cardId = jsonObject.get("cardId").getAsString
    val matcher = ROCmUtil.GPU_ID_PATTERN.matcher(cardId)
    if (matcher.matches) return matcher.group(1).toInt
    null
  }

  override def jsonObjectToAccelerator(jsonObject: JsonObject): Accelerator = {
    // Check if required field exists
    if (!jsonObject.has("Card Series")) throw new IllegalArgumentException("Missing required field: Card Series")
    val model = jsonObject.get("Card Series").getAsString
    val acceleratorId = extractAcceleratorId(jsonObject)
    val accelerator = new Accelerator(model, AcceleratorVendor.AMD, acceleratorId)
    // Set optional fields using GSON's has() method
    if (jsonObject.has("GPU use (%)")) accelerator.setUsagePercentage(jsonObject.get("GPU use (%)").getAsFloat)
    if (jsonObject.has("GPU Memory Allocated (VRAM%)")) accelerator.setMemoryUtilizationPercentage(jsonObject.get("GPU Memory Allocated (VRAM%)").getAsFloat)
    if (jsonObject.has("VRAM Total Memory (B)")) {
      val totalMemoryStr = jsonObject.get("VRAM Total Memory (B)").getAsString.trim
      val totalMemoryBytes = totalMemoryStr.toLong
      accelerator.setMemoryAvailableMegaBytes(IAcceleratorUtility.bytesToMegabytes(totalMemoryBytes))
    }
    if (jsonObject.has("VRAM Total Used Memory (B)")) {
      val usedMemoryStr = jsonObject.get("VRAM Total Used Memory (B)").getAsString
      val usedMemoryBytes = usedMemoryStr.toLong
      accelerator.setMemoryUtilizationMegabytes(IAcceleratorUtility.bytesToMegabytes(usedMemoryBytes))
    }
    accelerator
  }
}
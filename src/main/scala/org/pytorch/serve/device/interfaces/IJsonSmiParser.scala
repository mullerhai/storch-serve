package org.pytorch.serve.device.interfaces

import com.google.gson.{JsonElement, JsonObject}
import org.pytorch.serve.device.Accelerator
import org.slf4j.{Logger, LoggerFactory}

import java.util
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object IJsonSmiParser {
  val jsonSmiParserLogger: Logger = LoggerFactory.getLogger(classOf[IJsonSmiParser])
}

trait IJsonSmiParser {
  def jsonOutputToAccelerators(rootObject: JsonElement, parsedAcceleratorIds: mutable.LinkedHashSet[Integer]): List[Accelerator] = {
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

  def extractAcceleratorId(jsonObject: JsonObject): Integer

  def jsonObjectToAccelerator(jsonObject: JsonObject): Accelerator

  def extractAccelerators(rootObject: JsonElement): List[JsonObject]
}
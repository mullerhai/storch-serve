package org.pytorch.serve.device.interfaces

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util
import org.pytorch.serve.device.Accelerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object IJsonSmiParser {
  val jsonSmiParserLogger: Logger = LoggerFactory.getLogger(classOf[IJsonSmiParser])
}

trait IJsonSmiParser {
  def jsonOutputToAccelerators(rootObject: JsonElement, parsedAcceleratorIds: util.LinkedHashSet[Integer]): util.ArrayList[Accelerator] = {
    val accelerators = new util.ArrayList[Accelerator]
    val acceleratorObjects = extractAccelerators(rootObject)
//    import scala.collection.JavaConversions._
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

  def extractAcceleratorId(jsonObject: JsonObject): Integer

  def jsonObjectToAccelerator(jsonObject: JsonObject): Accelerator

  def extractAccelerators(rootObject: JsonElement): util.List[JsonObject]
}
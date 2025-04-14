package org.pytorch.serve.util.logging

import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.config.Node
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import org.apache.logging.log4j.core.layout.AbstractStringLayout
import org.apache.logging.log4j.message.Message
import org.pytorch.serve.metrics.Metric
//import org.pytorch.serve.metrics.Metric
import org.pytorch.serve.util.JsonUtils

import scala.jdk.CollectionConverters.*

@Plugin(name = "JSONPatternLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
object JSONPatternLayout {
  @PluginFactory 
  def createLayout = new JSONPatternLayout
}

@Plugin(name = "JSONPatternLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true) 
class JSONPatternLayout extends AbstractStringLayout(null, null, null) {
  override def toSerializable(event: LogEvent): String = {
    val eventMessage = event.getMessage
    if (eventMessage == null || eventMessage.getParameters == null) return null
    val parameters = eventMessage.getParameters
    for (obj <- parameters) {
      if (obj.isInstanceOf[Metric]) {
        val metric = obj.asInstanceOf[Metric]
        return JsonUtils.GSON_PRETTY.toJson(metric) + "\n"
      }
    }
    eventMessage.toString + '\n'
  }
}
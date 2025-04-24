package org.pytorch.serve.servingsdk.impl

import org.pytorch.serve.http.InvalidPluginException
import org.pytorch.serve.servingsdk.ModelServerEndpoint
import org.pytorch.serve.servingsdk.annotations.Endpoint
import org.pytorch.serve.servingsdk.annotations.helpers.EndpointTypes
import org.pytorch.serve.servingsdk.snapshot.SnapshotSerializer
import org.slf4j.{Logger, LoggerFactory}

import java.lang.annotation.Annotation
import java.util
import java.util.ServiceLoader
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, TreeMap, Map as MutableMap}
import scala.jdk.CollectionConverters.*
object PluginsManager {
  private val INSTANCE = new PluginsManager

  def getInstance: PluginsManager = INSTANCE
}

final class PluginsManager private {
  private val logger = LoggerFactory.getLogger(classOf[PluginsManager])
  private var inferenceEndpoints: MutableMap[String, ModelServerEndpoint] = null
  private var managementEndpoints: MutableMap[String, ModelServerEndpoint] = null

  def initialize(): Unit = {
    logger.info("Initializing plugins manager...")
    inferenceEndpoints = initInferenceEndpoints
    managementEndpoints = initManagementEndpoints
  }

  private def validateEndpointPlugin(a: Annotation, `type`: EndpointTypes) = a.isInstanceOf[Endpoint] && !(a.asInstanceOf[Endpoint]).urlPattern.isEmpty && a.asInstanceOf[Endpoint].endpointType == `type`

  def getSnapShotSerializer: SnapshotSerializer = {
    logger.info(" Loading snapshot serializer plugin...")
    val loader = ServiceLoader.load(classOf[SnapshotSerializer])
    if (loader.findFirst().isPresent) {
      val snapShotSerializer = loader.findFirst.get
      logger.info("Snapshot serializer plugin has been loaded successfully")
      return snapShotSerializer
    }
    null
  }

  def getInferenceEndpoints: Map[String, ModelServerEndpoint] = inferenceEndpoints.toMap

  def getManagementEndpoints: Map[String, ModelServerEndpoint] = managementEndpoints.toMap

  private def initInferenceEndpoints = getEndpoints(EndpointTypes.INFERENCE)

  private def initManagementEndpoints = getEndpoints(EndpointTypes.MANAGEMENT)

  @throws[InvalidPluginException]
  private def getEndpoints(`type`: EndpointTypes) = {
    val loader = ServiceLoader.load(classOf[ModelServerEndpoint])
    val ep = new mutable.HashMap[String, ModelServerEndpoint]
//    import scala.collection.JavaConversions._
    for (mep <- loader.asScala) {
      val modelServerEndpointClassObj = mep.getClass
      val annotations = modelServerEndpointClassObj.getAnnotations
      for (a <- annotations) {
        if (validateEndpointPlugin(a, `type`)) {
          if (ep.get(a.asInstanceOf[Endpoint].urlPattern) != null) throw new InvalidPluginException("Multiple plugins found for endpoint " + "\"" + a.asInstanceOf[Endpoint].urlPattern + "\"")
          logger.info("Loading plugin for endpoint {}", a.asInstanceOf[Endpoint].urlPattern)
          ep.put(a.asInstanceOf[Endpoint].urlPattern, mep)
        }
      }
    }
    ep
  }
}
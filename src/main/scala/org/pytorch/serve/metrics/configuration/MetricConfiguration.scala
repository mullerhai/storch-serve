package org.pytorch.serve.metrics.configuration

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.composer.ComposerException
import org.yaml.snakeyaml.constructor.Constructor
import scala.jdk.CollectionConverters._
object MetricConfiguration {
  private val logger = LoggerFactory.getLogger(classOf[MetricConfiguration])

  @throws[FileNotFoundException]
  @throws[ComposerException]
  @throws[RuntimeException]
  def loadConfiguration(configFilePath: String): MetricConfiguration = {
    val constructor = new Constructor(classOf[MetricConfiguration], new LoaderOptions)
    val yaml = new Yaml(constructor)
    val inputStream = new FileInputStream(new File(configFilePath))
    val config:MetricConfiguration = yaml.load(inputStream).asInstanceOf[MetricConfiguration]
    config.validate()
    logger.info("Successfully loaded metrics configuration from {}", configFilePath)
    config
  }
}

class MetricConfiguration {
  private var dimensions: util.List[String] = null
  @SuppressWarnings(Array("checkstyle:MemberName")) 
  private var ts_metrics:MetricTypes = null
  @SuppressWarnings(Array("checkstyle:MemberName")) 
  private var model_metrics:MetricTypes  = null

  def setDimensions(dimensions: util.List[String]): Unit = {
    this.dimensions = dimensions
  }

  def getDimensions: util.List[String] = this.dimensions

  @SuppressWarnings(Array("checkstyle:MethodName")) 
  def setTs_metrics(tsMetrics: MetricTypes): Unit = {
    this.ts_metrics = tsMetrics
  }

  @SuppressWarnings(Array("checkstyle:MethodName")) 
  def getTs_metrics: MetricTypes = this.ts_metrics

  @SuppressWarnings(Array("checkstyle:MethodName")) 
  def setModel_metrics(modelMetrics: MetricTypes): Unit = {
    // The Hostname dimension is included by default for model metrics
    modelMetrics.setCounter(this.addHostnameDimensionToMetrics(modelMetrics.getCounter))
    modelMetrics.setGauge(this.addHostnameDimensionToMetrics(modelMetrics.getGauge))
    modelMetrics.setHistogram(this.addHostnameDimensionToMetrics(modelMetrics.getHistogram))
    this.model_metrics = modelMetrics
  }

  @SuppressWarnings(Array("checkstyle:MethodName")) def getModel_metrics: MetricTypes = this.model_metrics

  def validate(): Unit = {
    if (this.ts_metrics != null) ts_metrics.validate()
    if (this.model_metrics != null) model_metrics.validate()
  }

  private def addHostnameDimensionToMetrics(metricsSpec: util.List[MetricSpecification]): util.List[MetricSpecification] = {
    if (metricsSpec == null) return metricsSpec
//    import scala.collection.JavaConversions._
    for (spec <- metricsSpec.asScala) {
      val dimensions = spec.getDimensions
      dimensions.add("Hostname")
      spec.setDimensions(dimensions)
    }
    metricsSpec
  }
}
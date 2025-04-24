package org.pytorch.serve.metrics.configuration

import org.slf4j.{Logger, LoggerFactory}
import org.yaml.snakeyaml.composer.ComposerException
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.{LoaderOptions, Yaml}

import java.io.{File, FileInputStream, FileNotFoundException}
import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
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
  private var dimensions: ListBuffer[String] = null
  @SuppressWarnings(Array("checkstyle:MemberName")) 
  private var ts_metrics:MetricTypes = null
  @SuppressWarnings(Array("checkstyle:MemberName")) 
  private var model_metrics:MetricTypes  = null

  def getDimensions: List[String] = this.dimensions.toList

  def setDimensions(dimensions: List[String]): Unit = {
    this.dimensions.appendAll(dimensions)
  }

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

  private def addHostnameDimensionToMetrics(metricsSpec: List[MetricSpecification]): List[MetricSpecification] = {
    if (metricsSpec == null) return metricsSpec
//    import scala.collection.JavaConversions._
    for (spec <- metricsSpec) {
      val dimensions = spec.getDimensions
      dimensions.appended("Hostname")
      spec.setDimensions(dimensions)
    }
    metricsSpec
  }
}
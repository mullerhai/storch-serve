package org.pytorch.serve.metrics

import org.pytorch.serve.metrics.configuration.{MetricConfiguration, MetricSpecification}
import org.pytorch.serve.util.ConfigManager
import org.slf4j.{Logger, LoggerFactory}

import java.io.FileNotFoundException
import java.util
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}
import scala.jdk.CollectionConverters.*

object MetricCache {
  private val logger = LoggerFactory.getLogger(classOf[MetricCache])
  private var instance: MetricCache = null

  @throws[FileNotFoundException]
  def init(): Unit = {
    if (instance != null) {
      logger.error("Skip initializing metrics cache since it has already been initialized")
      return
    }
    instance = new MetricCache
  }

  def getInstance: MetricCache = instance
}

@throws[FileNotFoundException]
final class MetricCache {
  var metricsFrontend = new ConcurrentHashMap[String, IMetric]
  var metricsBackend = new ConcurrentHashMap[String, IMetric]
  val metricsConfigPath = ConfigManager.getInstance.getMetricsConfigPath
  private var config: MetricConfiguration = null
  try this.config = MetricConfiguration.loadConfiguration(metricsConfigPath)
  catch {
    case e@(_: FileNotFoundException | _: RuntimeException) =>
      MetricCache.logger.error("Failed to load metrics configuration: ", e)
//      return
  }
  val metricsMode = ConfigManager.getInstance.getMetricsMode
  if (this.config.getTs_metrics != null) {
    addMetrics(this.metricsFrontend, this.config.getTs_metrics.getCounter, metricsMode, MetricBuilder.MetricType.COUNTER)
    addMetrics(this.metricsFrontend, this.config.getTs_metrics.getGauge, metricsMode, MetricBuilder.MetricType.GAUGE)
    addMetrics(this.metricsFrontend, this.config.getTs_metrics.getHistogram, metricsMode, MetricBuilder.MetricType.HISTOGRAM)
  }
  if (this.config.getModel_metrics != null) {
    addMetrics(this.metricsBackend, this.config.getModel_metrics.getCounter, metricsMode, MetricBuilder.MetricType.COUNTER)
    addMetrics(this.metricsBackend, this.config.getModel_metrics.getGauge, metricsMode, MetricBuilder.MetricType.GAUGE)
    addMetrics(this.metricsBackend, this.config.getModel_metrics.getHistogram, metricsMode, MetricBuilder.MetricType.HISTOGRAM)
  }

//  private var metricsFrontend: ConcurrentMap[String, IMetric] = null
//  private var metricsBackend: ConcurrentMap[String, IMetric] = null

  def addAutoDetectMetricBackend(parsedMetric: Metric) = {
    // The Hostname dimension is included by default for backend metrics
    val dimensionNames = parsedMetric.getDimensionNames
    dimensionNames.append("Hostname")
    val metric = MetricBuilder.build(ConfigManager.getInstance.getMetricsMode, MetricBuilder.MetricType.valueOf(parsedMetric.getType), parsedMetric.getMetricName, parsedMetric.getUnit, dimensionNames.toList)
    this.metricsBackend.putIfAbsent(parsedMetric.getMetricName, metric)
    metric
  }

  private def addMetrics(metricCache: ConcurrentMap[String, IMetric], metricsSpec: List[MetricSpecification], metricMode: MetricBuilder.MetricMode, metricType: MetricBuilder.MetricType): Unit = {
    if (metricsSpec == null) return
    //    import scala.collection.JavaConversions._
    for (spec <- metricsSpec) {
      metricCache.put(spec.getName, MetricBuilder.build(metricMode, metricType, spec.getName, spec.getUnit, spec.getDimensions))
    }
  }

  def getMetricFrontend(metricName: String) = metricsFrontend.get(metricName)

  def getMetricBackend(metricName: String) = metricsBackend.get(metricName)
}
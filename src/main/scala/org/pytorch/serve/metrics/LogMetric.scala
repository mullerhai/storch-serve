package org.pytorch.serve.metrics

import java.util
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import org.pytorch.serve.util.ConfigManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
object LogMetric {
  /**
   * Note: hostname, timestamp, and requestid(if available) are automatically added in log metric.
   */
  private val loggerTsMetrics = LoggerFactory.getLogger(ConfigManager.MODEL_SERVER_METRICS_LOGGER)
  private val loggerModelMetrics = LoggerFactory.getLogger(ConfigManager.MODEL_METRICS_LOGGER)
}

class LogMetric(`type`: MetricBuilder.MetricType, name: String, unit: String, dimensionNames: util.List[String]) extends IMetric(`type`, name, unit, dimensionNames) {
  override def addOrUpdate(dimensionValues: util.List[String], value: Double): Unit = {
    // Used for logging frontend metrics
    val metricString = this.buildMetricString(dimensionValues, value)
    LogMetric.loggerTsMetrics.info(metricString)
  }

  override def addOrUpdate(dimensionValues: util.List[String], requestIds: String, value: Double): Unit = {
    // Used for logging backend metrics
    val metricString = this.buildMetricString(dimensionValues, requestIds, value)
    LogMetric.loggerModelMetrics.info(metricString)
  }

  private def buildMetricString(dimensionValues: util.List[String], value: Double) = {
    val metricStringBuilder = new StringBuilder
    metricStringBuilder.append(this.name).append('.').append(this.unit).append(':').append(value).append("|#")
    // Exclude the final dimension which is expected to be Hostname
    val dimensionsCount = Math.min(this.dimensionNames.size - 1, dimensionValues.size - 1)
    val dimensions = new util.ArrayList[String]
    for (index <- 0 until dimensionsCount) {
      dimensions.add(this.dimensionNames.get(index) + ":" + dimensionValues.get(index))
    }
    metricStringBuilder.append(dimensions.stream.collect(Collectors.joining(",")))
    // The final dimension is expected to be Hostname
    metricStringBuilder.append("|#hostname:").append(dimensionValues.get(dimensionValues.size - 1))
    metricStringBuilder.append(",timestamp:").append(String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis)))
    metricStringBuilder.toString
  }

  private def buildMetricString(dimensionValues: util.List[String], requestIds: String, value: Double) = {
    val metricStringBuilder = new StringBuilder
    metricStringBuilder.append(this.name).append('.').append(this.unit).append(':').append(value).append("|#")
    // Exclude the final dimension which is expected to be Hostname
    val dimensionsCount = Math.min(this.dimensionNames.size - 1, dimensionValues.size - 1)
    val dimensions = new util.ArrayList[String]
    for (index <- 0 until dimensionsCount) {
      dimensions.add(this.dimensionNames.get(index) + ":" + dimensionValues.get(index))
    }
    metricStringBuilder.append(dimensions.stream.collect(Collectors.joining(",")))
    // The final dimension is expected to be Hostname
    metricStringBuilder.append("|#hostname:").append(dimensionValues.get(dimensionValues.size - 1))
    metricStringBuilder.append(",requestID:").append(requestIds)
    metricStringBuilder.append(",timestamp:").append(String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis)))
    metricStringBuilder.toString
  }
}
package org.pytorch.serve.metrics

import org.pytorch.serve.util.ConfigManager
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
object LogMetric {
  /**
   * Note: hostname, timestamp, and requestid(if available) are automatically added in log metric.
   */
  private val loggerTsMetrics = LoggerFactory.getLogger(ConfigManager.MODEL_SERVER_METRICS_LOGGER)
  private val loggerModelMetrics = LoggerFactory.getLogger(ConfigManager.MODEL_METRICS_LOGGER)
}

class LogMetric(`type`: MetricBuilder.MetricType, name: String, unit: String, dimensionNames: List[String]) extends IMetric(`type`, name, unit, dimensionNames) {
  override def addOrUpdate(dimensionValues: List[String], value: Double): Unit = {
    // Used for logging frontend metrics
    val metricString = this.buildMetricString(dimensionValues, value)
    LogMetric.loggerTsMetrics.info(metricString)
  }

  private def buildMetricString(dimensionValues: List[String], value: Double) = {
    val metricStringBuilder = new StringBuilder
    metricStringBuilder.append(this.name).append('.').append(this.unit).append(':').append(value).append("|#")
    // Exclude the final dimension which is expected to be Hostname
    val dimensionsCount = Math.min(this.dimensionNames.size - 1, dimensionValues.size - 1)
    val dimensions = new ListBuffer[String]
    for (index <- 0 until dimensionsCount) {
      dimensions.append(this.dimensionNames(index) + ":" + dimensionValues(index))
    }
    metricStringBuilder.append(dimensions.mkString(","))
    //    metricStringBuilder.append(dimensions.collect(Collectors.joining(",")))
    // The final dimension is expected to be Hostname
    metricStringBuilder.append("|#hostname:").append(dimensionValues(dimensionValues.size - 1))
    metricStringBuilder.append(",timestamp:").append(String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis)))
    metricStringBuilder.toString
  }

  override def addOrUpdate(dimensionValues: List[String], requestIds: String, value: Double): Unit = {
    // Used for logging backend metrics
    val metricString = this.buildMetricString(dimensionValues, requestIds, value)
    LogMetric.loggerModelMetrics.info(metricString)
  }

  private def buildMetricString(dimensionValues: List[String], requestIds: String, value: Double) = {
    val metricStringBuilder = new StringBuilder
    metricStringBuilder.append(this.name).append('.').append(this.unit).append(':').append(value).append("|#")
    // Exclude the final dimension which is expected to be Hostname
    val dimensionsCount = Math.min(this.dimensionNames.size - 1, dimensionValues.size - 1)
    val dimensions = new ListBuffer[String]
    for (index <- 0 until dimensionsCount) {
      dimensions.append(this.dimensionNames(index) + ":" + dimensionValues(index))
    }
    //    metricStringBuilder.append(dimensions.collect(Collectors.joining(",")))
    metricStringBuilder.append(dimensions.mkString(","))
    // The final dimension is expected to be Hostname
    metricStringBuilder.append("|#hostname:").append(dimensionValues(dimensionValues.size - 1))
    metricStringBuilder.append(",requestID:").append(requestIds)
    metricStringBuilder.append(",timestamp:").append(String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis)))
    metricStringBuilder.toString
  }
}
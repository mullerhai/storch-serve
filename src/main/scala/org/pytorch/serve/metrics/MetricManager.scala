package org.pytorch.serve.metrics

import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.wlm.ModelManager

import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit

object MetricManager {
  private val METRIC_MANAGER = new MetricManager

  def getInstance: MetricManager = METRIC_MANAGER

  def scheduleMetrics(configManager: ConfigManager): Unit = {
    val metricCollector = new MetricCollector(configManager)
    ModelManager.getInstance.getScheduler.scheduleAtFixedRate(metricCollector, 0, configManager.getMetricTimeInterval, TimeUnit.SECONDS)
  }
}

final class MetricManager private {

  private var metrics: List[Metric] = List.empty //Collections.emptyList

  def getMetrics: List[Metric] = metrics

  def setMetrics(metrics: List[Metric]): Unit = {
    this.metrics = metrics
  }
}
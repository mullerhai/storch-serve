package org.pytorch.serve.metrics

import org.pytorch.serve.metrics.MetricBuilder.MetricType.{COUNTER, GAUGE, HISTOGRAM}
import org.pytorch.serve.metrics.format.prometheous.{PrometheusCounter, PrometheusGauge, PrometheusHistogram}

import java.util

object MetricBuilder {
  private val legacyPrometheusMetrics = new util.HashSet[String](util.Arrays.asList("ts_inference_requests_total", "ts_inference_latency_microseconds", "ts_queue_latency_microseconds"))

  enum MetricMode:
    case PROMETHEUS, LOG, LEGACY
//  object MetricMode extends Enumeration {
//    type MetricMode = Value
//    val PROMETHEUS, LOG, LEGACY = Value
//  }
    
  enum MetricType:
    case COUNTER, GAUGE, HISTOGRAM

//  object MetricType extends Enumeration {
//    type MetricType = Value
//    val COUNTER, GAUGE, HISTOGRAM = Value
//  }

  def build(mode: MetricBuilder.MetricMode, `type`: MetricBuilder.MetricType, name: String, unit: String, dimensionNames: List[String]): IMetric = {
    if (mode eq MetricMode.PROMETHEUS) `type` match {
      case COUNTER =>
        return new PrometheusCounter(`type`, name, unit, dimensionNames)
      case GAUGE =>
        return new PrometheusGauge(`type`, name, unit, dimensionNames)
      case HISTOGRAM =>
        return new PrometheusHistogram(`type`, name, unit, dimensionNames)
      case _ =>
    }
    else if (mode eq MetricMode.LEGACY) {
      if (legacyPrometheusMetrics.contains(name)) return new PrometheusCounter(MetricType.COUNTER, name, unit, dimensionNames)
      return new LogMetric(`type`, name, unit, dimensionNames)
    }
    else return new LogMetric(`type`, name, unit, dimensionNames)
    null
  }
}

final class MetricBuilder private {
}
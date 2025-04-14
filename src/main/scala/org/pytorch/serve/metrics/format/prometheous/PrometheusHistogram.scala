package org.pytorch.serve.metrics.format.prometheous

import io.prometheus.client.Histogram
import java.util
import org.pytorch.serve.metrics.IMetric
import org.pytorch.serve.metrics.MetricBuilder

class PrometheusHistogram(`type`: MetricBuilder.MetricType, name: String, unit: String, dimensionNames: util.List[String]) extends IMetric(`type`, name, unit, dimensionNames) {
  final private var histogram = Histogram.build.name(this.name).labelNames(this.dimensionNames.toArray(new Array[String](this.dimensionNames.size))*).help("Torchserve prometheus histogram metric with unit: " + this.unit).register
  

  override def addOrUpdate(dimensionValues: util.List[String], value: Double): Unit = {
    this.histogram.labels(dimensionValues.toArray(new Array[String](dimensionValues.size))*).observe(value)
  }

  override def addOrUpdate(dimensionValues: util.List[String], requestIds: String, value: Double): Unit = {
    this.addOrUpdate(dimensionValues, value)
  }
}
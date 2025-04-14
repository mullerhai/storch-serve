package org.pytorch.serve.metrics.format.prometheous

import io.prometheus.client.Counter
import java.util
import org.pytorch.serve.metrics.IMetric
import org.pytorch.serve.metrics.MetricBuilder

class PrometheusCounter(`type`: MetricBuilder.MetricType, name: String, unit: String, dimensionNames: util.List[String]) extends IMetric(`type`, name, unit, dimensionNames) {
  final private var counter = Counter.build.name(this.name).labelNames(this.dimensionNames.toArray(new Array[String](this.dimensionNames.size))*).help("Torchserve prometheus counter metric with unit: " + this.unit).register
  

  override def addOrUpdate(dimensionValues: util.List[String], value: Double): Unit = {
    this.counter.labels(dimensionValues.toArray(new Array[String](dimensionValues.size))*).inc(value)
  }

  override def addOrUpdate(dimensionValues: util.List[String], requestIds: String, value: Double): Unit = {
    this.addOrUpdate(dimensionValues, value)
  }
}
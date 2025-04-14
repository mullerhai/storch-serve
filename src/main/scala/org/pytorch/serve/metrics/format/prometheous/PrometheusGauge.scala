package org.pytorch.serve.metrics.format.prometheous

import io.prometheus.client.Gauge
import java.util
import org.pytorch.serve.metrics.IMetric
import org.pytorch.serve.metrics.MetricBuilder

class PrometheusGauge(`type`: MetricBuilder.MetricType, name: String, unit: String, dimensionNames: util.List[String]) extends IMetric(`type`, name, unit, dimensionNames) {
  final private var gauge = Gauge.build.name(this.name).labelNames(this.dimensionNames.toArray(new Array[String](this.dimensionNames.size))*).help("Torchserve prometheus gauge metric with unit: " + this.unit).register
  

  override def addOrUpdate(dimensionValues: util.List[String], value: Double): Unit = {
    this.gauge.labels(dimensionValues.toArray(new Array[String](dimensionValues.size))*).set(value)
  }

  override def addOrUpdate(dimensionValues: util.List[String], requestIds: String, value: Double): Unit = {
    this.addOrUpdate(dimensionValues, value)
  }
}
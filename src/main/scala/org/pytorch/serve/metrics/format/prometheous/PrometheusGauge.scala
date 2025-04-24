package org.pytorch.serve.metrics.format.prometheous

import io.prometheus.client.Gauge
import org.pytorch.serve.metrics.{IMetric, MetricBuilder}

import java.util

class PrometheusGauge(`type`: MetricBuilder.MetricType, name: String, unit: String, dimensionNames: List[String]) extends IMetric(`type`, name, unit, dimensionNames) {
  //  final private var gauge = Gauge.build.name(this.name).labelNames(this.dimensionNames.toArray(new Array[String](this.dimensionNames.size))*).help("Storch Serve prometheus gauge metric with unit: " + this.unit).register
  final private var gauge = Gauge.build.name(this.name).labelNames(this.dimensionNames.toArray *).help("Storch Serve prometheus gauge metric with unit: " + this.unit).register

  override def addOrUpdate(dimensionValues: List[String], requestIds: String, value: Double): Unit = {
    this.addOrUpdate(dimensionValues, value)
  }

  override def addOrUpdate(dimensionValues: List[String], value: Double): Unit = {
    //    this.gauge.labels(dimensionValues.toArray(new Array[String](dimensionValues.size))*).set(value)
    this.gauge.labels(dimensionValues.toArray *).set(value)
  }
}
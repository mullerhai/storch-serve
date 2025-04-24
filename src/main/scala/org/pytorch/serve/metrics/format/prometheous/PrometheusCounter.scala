package org.pytorch.serve.metrics.format.prometheous

import io.prometheus.client.Counter
import org.pytorch.serve.metrics.{IMetric, MetricBuilder}

import java.util

class PrometheusCounter(`type`: MetricBuilder.MetricType, name: String, unit: String, dimensionNames: List[String]) extends IMetric(`type`, name, unit, dimensionNames) {
  //  final private var counter = Counter.build.name(this.name).labelNames(this.dimensionNames.toArray(new Array[String](this.dimensionNames.size))*).help("Storch Serve prometheus counter metric with unit: " + this.unit).register
  final private var counter = Counter.build.name(this.name).labelNames(this.dimensionNames.toArray *).help("Storch Serve prometheus counter metric with unit: " + this.unit).register

  override def addOrUpdate(dimensionValues: List[String], requestIds: String, value: Double): Unit = {
    this.addOrUpdate(dimensionValues, value)
  }

  override def addOrUpdate(dimensionValues: List[String], value: Double): Unit = {
    //    this.counter.labels(dimensionValues.toArray(new Array[String](dimensionValues.size))*).inc(value)
    this.counter.labels(dimensionValues.toArray *).inc(value)
  }
}
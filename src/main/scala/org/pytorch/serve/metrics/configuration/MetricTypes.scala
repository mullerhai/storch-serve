package org.pytorch.serve.metrics.configuration

import java.util
import scala.jdk.CollectionConverters._
class MetricTypes {
  private var counter: util.List[MetricSpecification] = null
  private var gauge: util.List[MetricSpecification] = null
  private var histogram: util.List[MetricSpecification] = null

  def setCounter(counter: util.List[MetricSpecification]): Unit = {
    this.counter = counter
  }

  def getCounter: util.List[MetricSpecification] = this.counter

  def setGauge(gauge: util.List[MetricSpecification]): Unit = {
    this.gauge = gauge
  }

  def getGauge: util.List[MetricSpecification] = this.gauge

  def setHistogram(histogram: util.List[MetricSpecification]): Unit = {
    this.histogram = histogram
  }

  def getHistogram: util.List[MetricSpecification] = this.histogram

  def validate(): Unit = {
    if (this.counter != null) {
//      import scala.collection.JavaConversions._
      for (spec <- this.counter.asScala) {
        spec.validate()
      }
    }
    if (this.gauge != null) {
//      import scala.collection.JavaConversions._
      for (spec <- this.gauge.asScala) {
        spec.validate()
      }
    }
    if (this.histogram != null) {
//      import scala.collection.JavaConversions._
      for (spec <- this.histogram.asScala) {
        spec.validate()
      }
    }
  }
}
package org.pytorch.serve.metrics.configuration

import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
class MetricTypes {
  private var counter: ListBuffer[MetricSpecification] = null
  private var gauge: ListBuffer[MetricSpecification] = null
  private var histogram: ListBuffer[MetricSpecification] = null

  def getCounter: List[MetricSpecification] = this.counter.toList

  def setCounter(counter: List[MetricSpecification]): Unit = {
    this.counter.appendAll(counter)
  }

  def getGauge: List[MetricSpecification] = this.gauge.toList

  def setGauge(gauge: List[MetricSpecification]): Unit = {
    this.gauge.appendAll(gauge)
  }

  def getHistogram: List[MetricSpecification] = this.histogram.toList

  def setHistogram(histogram: List[MetricSpecification]): Unit = {
    this.histogram.appendAll(histogram)
  }

  def validate(): Unit = {
    if (this.counter != null) {
//      import scala.collection.JavaConversions._
      for (spec <- this.counter) {
        spec.validate()
      }
    }
    if (this.gauge != null) {
//      import scala.collection.JavaConversions._
      for (spec <- this.gauge) {
        spec.validate()
      }
    }
    if (this.histogram != null) {
//      import scala.collection.JavaConversions._
      for (spec <- this.histogram) {
        spec.validate()
      }
    }
  }
}
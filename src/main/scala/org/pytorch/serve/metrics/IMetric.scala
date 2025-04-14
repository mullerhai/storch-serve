package org.pytorch.serve.metrics

import java.util

abstract class IMetric( var `type`: MetricBuilder.MetricType,  var name: String,  var unit: String, dimensionNames: util.List[String]) {
//  var dimensionNames = new util.ArrayList[String](dimensionNames)
//  protected var dimensionNames: util.List[String] = null

  def addOrUpdate(dimensionValues: util.List[String], value: Double): Unit

  def addOrUpdate(dimensionValues: util.List[String], requestIds: String, value: Double): Unit
}
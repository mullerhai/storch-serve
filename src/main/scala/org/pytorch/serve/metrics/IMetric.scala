package org.pytorch.serve.metrics

import java.util

abstract class IMetric(var `type`: MetricBuilder.MetricType, var name: String, var unit: String, dimensionNames: List[String]) {
//  var dimensionNames = new util.ArrayList[String](dimensionNames)
//  protected var dimensionNames: util.List[String] = null

  def addOrUpdate(dimensionValues: List[String], value: Double): Unit

  def addOrUpdate(dimensionValues: List[String], requestIds: String, value: Double): Unit
}
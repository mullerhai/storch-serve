package org.pytorch.serve.metrics.configuration

import java.util

class MetricSpecification {
  private var name: String = null
  private var unit: String = null
  private var dimensions: util.List[String] = null

  def setName(name: String): Unit = {
    this.name = name
  }

  def getName: String = this.name

  def setUnit(unit: String): Unit = {
    this.unit = unit
  }

  def getUnit: String = this.unit

  def setDimensions(dimensions: util.List[String]): Unit = {
    this.dimensions = dimensions
  }

  def getDimensions: util.List[String] = this.dimensions

  override def toString: String = "name: " + this.name + ", unit: " + this.unit + ", dimensions: " + this.dimensions

  def validate(): Unit = {
    if (this.name == null || this.name.isEmpty) throw new RuntimeException("Metric name cannot be empty. " + this)
    if (this.unit == null || this.unit.isEmpty) throw new RuntimeException("Metric unit cannot be empty. " + this)
  }
}
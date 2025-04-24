package org.pytorch.serve.metrics.configuration

import java.util
import scala.collection.mutable.ListBuffer

class MetricSpecification {
  private var name: String = null
  private var unit: String = null
  private var dimensions: ListBuffer[String] = null

  def setName(name: String): Unit = {
    this.name = name
  }

  def getName: String = this.name

  def setUnit(unit: String): Unit = {
    this.unit = unit
  }

  def getUnit: String = this.unit

  def getDimensions: List[String] = this.dimensions.toList

  def setDimensions(dimensions: List[String]): Unit = {
    this.dimensions.appendAll(dimensions)
  }

  override def toString: String = "name: " + this.name + ", unit: " + this.unit + ", dimensions: " + this.dimensions

  def validate(): Unit = {
    if (this.name == null || this.name.isEmpty) throw new RuntimeException("Metric name cannot be empty. " + this)
    if (this.unit == null || this.unit.isEmpty) throw new RuntimeException("Metric unit cannot be empty. " + this)
  }
}
package org.pytorch.serve.metrics

import com.google.gson.annotations.SerializedName
import java.util
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._

object Metric {
  private val PATTERN = Pattern.compile("\\s*([\\w\\s]+)\\.([\\w\\s]+):([0-9\\-,.e]+)\\|#([^|]*)(\\|#type:([^|,]+))?\\|#hostname:([^,]+),([^,]+)(,(.*))?")

  def parse(line: String): Metric = {
    // DiskAvailable.Gigabytes:311|#Level:Host,hostname:localhost
    val matcher = PATTERN.matcher(line)
    if (!matcher.matches) return null
    val metric = new Metric
    metric.setMetricName(matcher.group(1))
    metric.setUnit(matcher.group(2))
    metric.setValue(matcher.group(3))
    val dimensions = matcher.group(4)
    metric.setType(matcher.group(6))
    metric.setHostName(matcher.group(7))
    metric.setTimestamp(matcher.group(8))
    metric.setRequestId(matcher.group(10))
    if (dimensions != null) {
      val dimension = dimensions.split(",")
      val list = new util.ArrayList[Dimension](dimension.length)
      for (dime <- dimension) {
        val pair = dime.split(":")
        if (pair.length == 2) list.add(new Dimension(pair(0), pair(1)))
      }
      metric.setDimensions(list)
    }
    metric
  }
}

class Metric {
  @SerializedName("MetricName") 
  private var metricName:String = null
  @SerializedName("Value") 
  private var value:String = null
  @SerializedName("Unit") 
  private var unit:String = null
  @SerializedName("Type") 
  private var `type`:String = null
  @SerializedName("Dimensions") 
  private var dimensions: util.List[Dimension] = null
  @SerializedName("DimensionNames") 
  private var dimensionNames = new util.ArrayList[String]
  @SerializedName("DimensionValues") 
  private var dimensionValues = new util.ArrayList[String]
  @SerializedName("Timestamp") 
  private var timestamp:String = null
  @SerializedName("RequestId") 
  private var requestId:String = null
  @SerializedName("HostName")
  private var hostName:String = null

  def this(metricName: String, value: String, unit: String, `type`: String, hostName: String, dimensions: Dimension*)= {
    this()
    this.metricName = metricName
    this.value = value
    this.unit = unit
    this.`type` = `type`
    this.hostName = hostName
    this.setDimensions(util.Arrays.asList(dimensions*))
    this.timestamp = String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis))
  }

  def getHostName: String = hostName

  def setHostName(hostName: String): Unit = {
    this.hostName = hostName
  }

  def getRequestId: String = requestId

  def setRequestId(requestId: String): Unit = {
    this.requestId = requestId
  }

  def getMetricName: String = metricName

  def setMetricName(metricName: String): Unit = {
    this.metricName = metricName
  }

  def getValue: String = value

  def setValue(value: String): Unit = {
    this.value = value
  }

  def getUnit: String = unit

  def setUnit(unit: String): Unit = {
    this.unit = unit
  }

  def getType: String = `type`

  def setType(`type`: String): Unit = {
    this.`type` = `type`
  }

  def getDimensions: util.List[Dimension] = dimensions

  def getDimensionNames: util.List[String] = this.dimensionNames

  def getDimensionValues: util.List[String] = this.dimensionValues

  def setDimensions(dimensions: util.List[Dimension]): Unit = {
    this.dimensions = dimensions
//    this.dimensionNames = new util.ArrayList[String]
//    this.dimensionValues = new util.ArrayList[String]
//    import scala.collection.JavaConversions._
    for (dimension <- dimensions.asScala) {
      this.dimensionNames.add(dimension.name)
      this.dimensionValues.add(dimension.value)
    }
  }

  def getTimestamp: String = timestamp

  def setTimestamp(timestamp: String): Unit = {
    this.timestamp = timestamp
  }

  override def toString: String = {
    val sb = new StringBuilder(128)
    sb.append(metricName).append('.').append(unit).append(':').append(getValue).append("|#")
    var first = true
//    import scala.collection.JavaConversions._
    for (dimension <- getDimensions.asScala) {
      if (first) first = false
      else sb.append(',')
      sb.append(dimension.name).append(':').append(dimension.value)
    }
    sb.append("|#hostname:").append(hostName)
    if (requestId != null) sb.append(",requestID:").append(requestId)
    sb.append(",timestamp:").append(timestamp)
    sb.toString
  }
}
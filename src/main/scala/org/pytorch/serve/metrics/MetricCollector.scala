package org.pytorch.serve.metrics

import org.apache.commons.io.IOUtils
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.messages.EnvironmentUtils
import org.pytorch.serve.wlm.{ModelManager, WorkerThread}
import org.slf4j.{Logger, LoggerFactory}

import java.io.*
import java.nio.charset.StandardCharsets
import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks.{break, breakable}
object MetricCollector {
  private val logger = LoggerFactory.getLogger(classOf[MetricCollector])
}

class MetricCollector( var configManager: ConfigManager) extends Runnable {
  final private var metricCache = MetricCache.getInstance
//  final private var metricCache: MetricCache = null

  override def run(): Unit = {
    try {
      // Collect System level Metrics
      val args = new ListBuffer[String]
      args.append(configManager.getPythonExecutable)
      var systemMetricsCmd = configManager.getSystemMetricsCmd
      if (systemMetricsCmd.isEmpty) systemMetricsCmd = String.format("%s --gpu %s", "ts/metrics/metric_collector.py", String.valueOf(configManager.getNumberOfGpu))
      args.addAll(util.Arrays.asList(systemMetricsCmd.split("\\s+") *).asScala)
      val workingDir = new File(configManager.getModelServerHome)
      val envp = EnvironmentUtils.getEnvString(workingDir.getAbsolutePath, null, null)
      //      val p = Runtime.getRuntime.exec(args.toArray(new Array[String](0)), envp, workingDir) // NOPMD
      val p = Runtime.getRuntime.exec(args.toArray(), envp, workingDir) // NOPMD
      val modelManager = ModelManager.getInstance
      val workerMap = modelManager.getWorkers
      try {
        val os = p.getOutputStream
        try writeWorkerPids(workerMap, os)
        finally if (os != null) os.close()
      }
      new Thread(() => {
        try {
          val error = IOUtils.toString(p.getErrorStream, StandardCharsets.UTF_8)
          if (!error.isEmpty) MetricCollector.logger.error(error)
        } catch {
          case e: IOException =>
            MetricCollector.logger.error("", e)
        }
      }).start()
      val metricManager = MetricManager.getInstance
      try {
        val reader = new BufferedReader(new InputStreamReader(p.getInputStream, StandardCharsets.UTF_8))
        try {
          val metricsSystem = new ListBuffer[Metric]
          metricManager.setMetrics(metricsSystem.toList)
          var line: String = null
          breakable(
            while (reader.readLine!= null) {
              line = reader.readLine
              if (line.isEmpty) break //todo: break is not supported
              val metric = Metric.parse(line)
              if (metric == null) MetricCollector.logger.warn("Parse metrics failed: " + line)
              else {
                if (this.metricCache.getMetricFrontend(metric.getMetricName) != null) try {
                  // Frontend metrics by default have the last dimension as Hostname
                  val dimensionValues = metric.getDimensionValues
                  dimensionValues.append(metric.getHostName)
                  this.metricCache.getMetricFrontend(metric.getMetricName).addOrUpdate(dimensionValues.toList, metric.getValue.toDouble)
                } catch {
                  case e: Exception =>
                    MetricCollector.logger.error("Failed to update frontend metric ", metric.getMetricName, ": ", e)
                }
                metricsSystem.append(metric)
              }
            }
          )
      
          // Collect process level metrics
          while (reader.readLine != null) {
            line = reader.readLine
            val tokens = line.split(":")
            breakable(
              if (tokens.length != 2) break() // continue //todo: continue is not supported
            )
            
            val pid = Integer.valueOf(tokens(0))
            val worker = workerMap.get(pid)
            if worker.isDefined then worker.get.setMemory(java.lang.Long.parseLong(tokens(1)))
          }
        } finally if (reader != null) reader.close()
      }
    } catch {
      case e: IOException =>
        MetricCollector.logger.error("", e)
    }
  }

  @throws[IOException]
  private def writeWorkerPids(workerMap: Map[Integer, WorkerThread], os: OutputStream): Unit = {
    var first = true
    for (pid <- workerMap.keySet) {
      breakable(
        if (pid < 0) {
          MetricCollector.logger.warn("worker pid is not available yet.")
          break()
         // continue //todo: continue is not supported
        }
      )
    
      if (first) first = false
      else IOUtils.write(",", os, StandardCharsets.UTF_8)
      IOUtils.write(pid.toString, os, StandardCharsets.UTF_8)
    }
    os.write('\n')
  }
}
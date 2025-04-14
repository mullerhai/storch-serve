package org.pytorch.serve.wlm

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util
import java.util.Scanner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.pytorch.serve.archive.model.Manifest.RuntimeType.LSP
import org.pytorch.serve.archive.model.{Manifest, ModelConfig}
import org.pytorch.serve.archive.model.ModelConfig.ParallelType
//import org.pytorch.serve.ensemble.WorkflowManifest.RuntimeType
//import org.pytorch.serve.ensemble.WorkflowManifest.RuntimeType.LSP
import org.pytorch.serve.metrics.Metric
import org.pytorch.serve.metrics.MetricCache
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.Connector
import org.pytorch.serve.util.messages.EnvironmentUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.util.control.Breaks.{break, breakable}
import org.pytorch.serve.wlm.Model

import scala.jdk.CollectionConverters.*

object WorkerLifeCycle {
  private val logger = LoggerFactory.getLogger(classOf[WorkerLifeCycle])
  private val PID_LOG_PATTERN = Pattern.compile(".*\\[PID\\](\\d+)$")
  private val METRIC_LOG_START_SUBSTRING = "[METRICS]"

   object ReaderThread {
    private val METRIC_PATTERN = Pattern.compile("^(INFO > )?(\\[METRICS])(.*)")
    // TODO: Fix logging format in cpp backend
    private val WORKER_START_PATTERN = Pattern.compile("(.*)(INFO > )?(Torch worker started.)$")
    private val WORKER_PID_PATTERN = Pattern.compile("^(INFO > )?(\\[PID])(\\d+)$")
    private val loggerModelOutput = LoggerFactory.getLogger(ConfigManager.MODEL_LOGGER)
  }

  final  class ReaderThread(name: String, private var is: InputStream, private var error: Boolean, private var lifeCycle: WorkerLifeCycle) extends Thread(name + (if (error) "-stderr"
  else "-stdout")) {
    
    final private var metricCache: MetricCache = MetricCache.getInstance
    private val isRunning = new AtomicBoolean(true)

    override def run(): Unit = {
      try {
        val scanner = new Scanner(is, StandardCharsets.UTF_8.name)
        try while (scanner.hasNextLine) {
          val result = scanner.nextLine
          var matcher = ReaderThread.METRIC_PATTERN.matcher(result)
          breakable(
            if (matcher.matches) {
              logger.info("result={}, pattern={}", result, matcher.group(2))
              val parsedMetric = Metric.parse(matcher.group(3))
              breakable(
                if (parsedMetric == null) {
                  logger.error("Failed to parse metrics line: \"{}\".", result)
                  //                continue //todo: continue is not supported
                  break()
                }
              )

              try {
                if (this.metricCache.getMetricBackend(parsedMetric.getMetricName) == null) {
                  breakable(
                    if (!lifeCycle.configManager.isModelMetricsAutoDetectEnabled)
                      break()
                    //continue //todo: continue is not supported
                  )

                  logger.info("Registering auto detected backend metric: {}", parsedMetric)
                  this.metricCache.addAutoDetectMetricBackend(parsedMetric)
                }
                // Hostname is added as a dimension by default to backend metrics
                val dimensionValues = parsedMetric.getDimensionValues
                dimensionValues.add(parsedMetric.getHostName)
                this.metricCache.getMetricBackend(parsedMetric.getMetricName).addOrUpdate(dimensionValues, parsedMetric.getRequestId, parsedMetric.getValue.toDouble)
              } catch {
                case e: Exception =>
                  logger.error("Failed to update backend metric ", parsedMetric.getMetricName, ": ", e)
              }
              break()
//              continue //todo: continue is not supported
            }
          )
         
          matcher = ReaderThread.WORKER_START_PATTERN.matcher(result)
          if (matcher.matches) lifeCycle.setSuccess(true)
          else {
            matcher = ReaderThread.WORKER_PID_PATTERN.matcher(result)
            if (matcher.matches) lifeCycle.setPid(matcher.group(3).toInt)
          }
          if (error) ReaderThread.loggerModelOutput.warn(result)
          else ReaderThread.loggerModelOutput.info(result)
        }
        catch {
          case e: Exception =>
            logger.error("Couldn't create scanner - {}", getName, e)
        } finally {
          logger.info("Stopped Scanner - {}", getName)
          lifeCycle.setSuccess(false)
          try is.close()
          catch {
            case e: IOException =>
              logger.error("Failed to close stream for thread {}", this.getName, e)
          }
          if (scanner != null) scanner.close()
        }
      }
    }
  }
}

class WorkerLifeCycle( var configManager: ConfigManager,  var model: Model) {
  private val modelManager = ModelManager.getInstance
  private var pid = -1
  private var process: Process = null
  private var latch: CountDownLatch = null
  private var success = false
  private var connector: Connector = null
  private var errReader: WorkerLifeCycle.ReaderThread = null
  private var outReader: WorkerLifeCycle.ReaderThread = null
  private var numWorker = model.getMinWorkers
  private var currNumRunningWorkers = modelManager.getNumRunningWorkers(model.getModelVersionName)

  def getProcess: Process = process

  def launcherArgsToList(launcherArgs: String): util.ArrayList[String] = {
    val arrlist = new util.ArrayList[String]
    arrlist.add("-m")
    arrlist.add("torch.backends.xeon.run_cpu")
    if (launcherArgs != null && launcherArgs.length > 1) {
      val argarray = launcherArgs.split(" ")
      for (i <- 0 until argarray.length) {
        arrlist.add(argarray(i))
      }
    }
    arrlist
  }

  @throws[WorkerInitializationException]
  @throws[InterruptedException]
  def isLauncherAvailable(launcherArgs: String): Boolean = {
    var launcherAvailable = false
    val cmd = new util.ArrayList[String]
    cmd.add("python")
    val args = launcherArgsToList(launcherArgs)
    cmd.addAll(args)
    cmd.add("--no_python")
    // try launching dummy command to check launcher availability
    val dummyCmd = "hostname"
    cmd.add(dummyCmd)
    var cmdList = new Array[String](cmd.size)
    cmdList = cmd.toArray(cmdList)
    WorkerLifeCycle.logger.debug("launcherAvailable cmdline: {}", cmd.toString)
    try {
      val processLauncher = Runtime.getRuntime.exec(cmdList)
      val ret = processLauncher.waitFor
      launcherAvailable = ret == 0
    } catch {
      case e@(_: IOException | _: InterruptedException) =>
        throw new WorkerInitializationException("Failed to start launcher", e)
    }
    launcherAvailable
  }

  @throws[WorkerInitializationException]
  @throws[InterruptedException]
  def startWorker(port: Int, deviceIds: String): Unit = {
    model.getRuntimeType match {
      case Manifest.RuntimeType.LSP =>
        WorkerLifeCycle.logger.info("LSP startWorker")
        startWorkerCPP(port, "LSP", deviceIds)
      case _ =>
        startWorkerPython(port, deviceIds)
    }
  }

  @throws[WorkerInitializationException]
  @throws[InterruptedException]
  private def startWorkerPython(port: Int, deviceIds: String): Unit = {
    val workingDir = new File(configManager.getModelServerHome)
    var modelPath: File = null
    setPort(port)
    try {
      modelPath = model.getModelDir
      // Test if modelPath is valid
      modelPath.getCanonicalFile
    } catch {
      case e: IOException =>
        throw new WorkerInitializationException("Failed get TS home directory", e)
    }
    val argl = new util.ArrayList[String]
    val envp = new util.ArrayList[String]
    envp.addAll(util.Arrays.asList(EnvironmentUtils.getEnvString(workingDir.getAbsolutePath, modelPath.getAbsolutePath, model.getModelArchive.getManifest.getModel.getHandler)*))
    if (model.getParallelLevel > 0) if (model.getParallelType ne ParallelType.CUSTOM) attachRunner(argl, envp, port, deviceIds)
    else {
      if (deviceIds != null) {
        val visibleDeviceEnvName = configManager.systemInfo.getVisibleDevicesEnvName
        envp.add(visibleDeviceEnvName + "=" + deviceIds)
      }
      argl.add(EnvironmentUtils.getPythonRunTime(model))
    }
    else if (model.getParallelLevel == 0) argl.add(EnvironmentUtils.getPythonRunTime(model))
    if (configManager.isCPULauncherEnabled) {
      val launcherArgs = configManager.getCPULauncherArgs
      val launcherAvailable = isLauncherAvailable(launcherArgs)
      if (launcherAvailable) {
        val args = launcherArgsToList(launcherArgs)
        argl.addAll(args)
        // multi-worker core pinning
        if (this.numWorker > 1) {
          argl.add("--ninstances")
          argl.add(String.valueOf(this.numWorker))
          argl.add("--rank")
          // instance_idx is 0-indexed
          argl.add(String.valueOf(this.currNumRunningWorkers))
        }
      }
      else WorkerLifeCycle.logger.warn("torch.backends.xeon.run_cpu is not available. Proceeding without worker core pinning. For better performance, please make sure torch.backends.xeon.run_cpu is available.")
    }
    argl.add(new File(workingDir, "ts/model_service_worker.py").getAbsolutePath)
    argl.add("--sock-type")
    argl.add(connector.getSocketType)
    argl.add(if (connector.isUds) "--sock-name" else "--port")
    argl.add(connector.getSocketPath)
    argl.add("--metrics-config")
    argl.add(configManager.getMetricsConfigPath)
    if (model.isAsyncCommunication) argl.add("--async")
    try {
      latch = new CountDownLatch(if (model.getParallelLevel > 0 && (model.getParallelType ne ParallelType.CUSTOM)) model.getParallelLevel
      else 1)
      val args = argl.toArray(new Array[String](argl.size))
      val envs = envp.toArray(new Array[String](envp.size))
      WorkerLifeCycle.logger.debug("Worker cmdline: {}", argl.toString)
      this.synchronized {
        process = Runtime.getRuntime.exec(args, envs, modelPath)
        val threadName = "W-" + port + '-' + model.getModelVersionName.getVersionedModelName
        errReader = new WorkerLifeCycle.ReaderThread(threadName, process.getErrorStream, true, this)
        outReader = new WorkerLifeCycle.ReaderThread(threadName, process.getInputStream, false, this)
        errReader.start()
        outReader.start()
      }
      if (latch.await(2, TimeUnit.MINUTES)) {
        if (!success) throw new WorkerInitializationException("Backend stream closed.")
        return
      }
      throw new WorkerInitializationException("Backend worker startup time out.")
    } catch {
      case e: IOException =>
        throw new WorkerInitializationException("Failed start worker process", e)
    } finally if (!success) exit()
  }

  @throws[WorkerInitializationException]
  @throws[InterruptedException]
  private def startWorkerCPP(port: Int, runtimeType: String, deviceIds: String): Unit = {
    val workingDir = new File(configManager.getModelServerHome)
    var modelPath: File = null
    setPort(port)
    try modelPath = model.getModelDir.getCanonicalFile
    catch {
      case e: IOException =>
        throw new WorkerInitializationException("Failed get TS home directory", e)
    }
    val argl = new util.ArrayList[String]
    val cppBackendBin = new File(workingDir, "ts/cpp/bin/model_worker_socket")
    val cppBackendLib = new File(workingDir, "ts/cpp/lib")
    if (!cppBackendBin.exists) throw new WorkerInitializationException("model_worker_socket not found")
    if (!cppBackendLib.exists) throw new WorkerInitializationException("model_worker cpp library not found")
    argl.add(cppBackendBin.getAbsolutePath)
    argl.add("--sock_type")
    argl.add(connector.getSocketType)
    argl.add(if (connector.isUds) "--sock_name"
    else "--port")
    argl.add(connector.getSocketPath)
    argl.add("--runtime_type")
    argl.add(runtimeType)
    argl.add("--model_dir")
    argl.add(modelPath.getAbsolutePath)
    if (ConfigManager.getInstance.getTsCppLogConfig != null) {
      argl.add("--logger_config_path")
      argl.add(ConfigManager.getInstance.getTsCppLogConfig)
    }
    argl.add("--metrics_config_path")
    argl.add(configManager.getMetricsConfigPath)
    val envp = EnvironmentUtils.getCppEnvString(cppBackendLib.getAbsolutePath)
    try {
      latch = new CountDownLatch(1)
      val args = argl.toArray(new Array[String](argl.size))
      WorkerLifeCycle.logger.debug("Worker cmdline: {}", argl.toString)
      this.synchronized {
        process = Runtime.getRuntime.exec(args, envp, modelPath)
        val threadName = "W-" + port + '-' + model.getModelVersionName.getVersionedModelName
        errReader = new WorkerLifeCycle.ReaderThread(threadName, process.getErrorStream, true, this)
        outReader = new WorkerLifeCycle.ReaderThread(threadName, process.getInputStream, false, this)
        errReader.start()
        outReader.start()
      }
      if (latch.await(2, TimeUnit.MINUTES)) {
        if (!success) throw new WorkerInitializationException("Backend stream closed.")
        return
      }
      throw new WorkerInitializationException("Backend worker startup time out.")
    } catch {
      case e: IOException =>
        throw new WorkerInitializationException("Failed start worker process", e)
    } finally if (!success) exit()
  }

  private def attachRunner(argl: util.ArrayList[String], envp: util.List[String], port: Int, deviceIds: String): Unit = {
    envp.add("LOGLEVEL=INFO")
    if (deviceIds != null) envp.add("CUDA_VISIBLE_DEVICES=" + deviceIds)
    val torchRun = model.getModelArchive.getModelConfig.getTorchRun
    envp.add(String.format("OMP_NUM_THREADS=%d", torchRun.getOmpNumberThreads))
    argl.add("torchrun")
    argl.add("--nnodes")
    argl.add(String.valueOf(torchRun.getNnodes))
    argl.add("--nproc-per-node")
    argl.add(String.valueOf(torchRun.getNprocPerNode))
    argl.add("--log-dir")
    argl.add(ConfigManager.getInstance.getTorchRunLogDir)
    argl.add("--rdzv-backend")
    argl.add(torchRun.getRdzvBackend)
    if (torchRun.getRdzvEndpoint != null) {
      argl.add("--rdzv-endpoint")
      argl.add(torchRun.getRdzvEndpoint)
    }
    argl.add("--rdzv-id")
    argl.add(String.format("%s_%d", model.getModelName, port))
    if (torchRun.getMasterAddr != null) {
      argl.add("--master-addr")
      argl.add(torchRun.getMasterAddr)
      argl.add("--master-port")
      argl.add(String.valueOf(torchRun.getMasterPort))
    }
    argl.add("--max-restarts")
    argl.add(String.valueOf(1))
  }

  def exit(): Unit = {
    if (process != null) {
      process.destroyForcibly
      connector.clean()
    }
  }

  def getExitValue: Integer = {
    if (process != null && !process.isAlive) return process.exitValue
    null
  }

  def setSuccess(success: Boolean): Unit = {
    this.success = success
    latch.countDown()
  }

  def getPid: Int = pid

  def setPid(pid: Int): Unit = {
    this.pid = pid
  }

  private def setPort(port: Int): Unit = {
    connector = new Connector(port)
  }
}
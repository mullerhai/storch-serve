package org.pytorch.serve.wlm

import org.pytorch.serve.archive.model.Manifest.RuntimeType.LSP
import org.pytorch.serve.archive.model.ModelConfig.ParallelType
import org.pytorch.serve.archive.model.{Manifest, ModelConfig}

import java.io.{File, IOException, InputStream}
import java.nio.charset.StandardCharsets
import java.util
import java.util.Scanner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.regex.{Matcher, Pattern}
import scala.collection.mutable.ListBuffer
//import org.pytorch.serve.ensemble.WorkflowManifest.RuntimeType
//import org.pytorch.serve.ensemble.WorkflowManifest.RuntimeType.LSP
import org.pytorch.serve.metrics.{Metric, MetricCache}
import org.pytorch.serve.util.messages.EnvironmentUtils
import org.pytorch.serve.util.{ConfigManager, Connector}
import org.pytorch.serve.wlm.Model
import org.slf4j.{Logger, LoggerFactory}
//import scala.sys.process.{Process,ProcessBuilder,ProcessImplicits}
import scala.jdk.CollectionConverters.*
//import scala.sys.process.processInternal.JProcess
import scala.util.control.Breaks.{break, breakable}

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
                dimensionValues.append(parsedMetric.getHostName)
                this.metricCache.getMetricBackend(parsedMetric.getMetricName).addOrUpdate(dimensionValues.toList, parsedMetric.getRequestId, parsedMetric.getValue.toDouble)
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
  private var processHandle: java.lang.ProcessHandle = null
  private var latch: CountDownLatch = null
  private var success = false
  private var connector: Connector = null
  private var errReader: WorkerLifeCycle.ReaderThread = null
  private var outReader: WorkerLifeCycle.ReaderThread = null
  private var numWorker = model.getMinWorkers
  private var currNumRunningWorkers = modelManager.getNumRunningWorkers(model.getModelVersionName)

  def getProcess: Process = process
  def getProcessHandle: java.lang.ProcessHandle = processHandle
//  def getPid = process.pid() //.toHandle.pid()
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
    val argl = new ListBuffer[String]
    val envp = new ListBuffer[String]
    envp.addAll(util.Arrays.asList(EnvironmentUtils.getEnvString(workingDir.getAbsolutePath, modelPath.getAbsolutePath, model.getModelArchive.getManifest.getModel.getHandler) *).asScala)
    if (model.getParallelLevel > 0) if (model.getParallelType ne ParallelType.CUSTOM) attachRunner(argl, envp, port, deviceIds)
    else {
      if (deviceIds != null) {
        val visibleDeviceEnvName = configManager.systemInfo.getVisibleDevicesEnvName
        envp.append(visibleDeviceEnvName + "=" + deviceIds)
      }
      argl.append(EnvironmentUtils.getPythonRunTime(model))
    }
    else if (model.getParallelLevel == 0) argl.append(EnvironmentUtils.getPythonRunTime(model))
    if (configManager.isCPULauncherEnabled) {
      val launcherArgs = configManager.getCPULauncherArgs
      val launcherAvailable = isLauncherAvailable(launcherArgs)
      if (launcherAvailable) {
        val args = launcherArgsToList(launcherArgs)
        argl.addAll(args)
        // multi-worker core pinning
        if (this.numWorker > 1) {
          argl.append("--ninstances")
          argl.append(String.valueOf(this.numWorker))
          argl.append("--rank")
          // instance_idx is 0-indexed
          argl.append(String.valueOf(this.currNumRunningWorkers))
        }
      }
      else WorkerLifeCycle.logger.warn("torch.backends.xeon.run_cpu is not available. Proceeding without worker core pinning. For better performance, please make sure torch.backends.xeon.run_cpu is available.")
    }
    argl.append(new File(workingDir, "ts/model_service_worker.py").getAbsolutePath)
    argl.append("--sock-type")
    argl.append(connector.getSocketType)
    argl.append(if (connector.isUds) "--sock-name" else "--port")
    argl.append(connector.getSocketPath)
    argl.append("--metrics-config")
    argl.append(configManager.getMetricsConfigPath)
    if (model.isAsyncCommunication) argl.append("--async")
    try {
      latch = new CountDownLatch(if (model.getParallelLevel > 0 && (model.getParallelType ne ParallelType.CUSTOM)) model.getParallelLevel
      else 1)
      val args = argl.toArray() //new Array[String](argl.size))
      val envs = envp.toArray() //new Array[String](envp.size))
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
  def isLauncherAvailable(launcherArgs: String): Boolean = {
    var launcherAvailable = false
    val cmd = new ListBuffer[String]
    cmd.append("python")
    val args = launcherArgsToList(launcherArgs)
    cmd.addAll(args)
    cmd.append("--no_python")
    // try launching dummy command to check launcher availability
    val dummyCmd = "hostname"
    cmd.append(dummyCmd)
    var cmdList = new Array[String](cmd.size)
    cmdList = cmd.toArray() //cmdList)
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

  def launcherArgsToList(launcherArgs: String): List[String] = {
    val arrlist = new ListBuffer[String]
    arrlist.append("-m")
    arrlist.append("torch.backends.xeon.run_cpu")
    if (launcherArgs != null && launcherArgs.length > 1) {
      val argarray = launcherArgs.split(" ")
      for (i <- 0 until argarray.length) {
        arrlist.append(argarray(i))
      }
    }
    arrlist.toList
  }

  private def attachRunner(argl: ListBuffer[String], envp: ListBuffer[String], port: Int, deviceIds: String): Unit = {
    envp.append("LOGLEVEL=INFO")
    if (deviceIds != null) envp.append("CUDA_VISIBLE_DEVICES=" + deviceIds)
    val torchRun = model.getModelArchive.getModelConfig.getTorchRun
    envp.append(String.format("OMP_NUM_THREADS=%d", torchRun.getOmpNumberThreads))
    argl.append("torchrun")
    argl.append("--nnodes")
    argl.append(String.valueOf(torchRun.getNnodes))
    argl.append("--nproc-per-node")
    argl.append(String.valueOf(torchRun.getNprocPerNode))
    argl.append("--log-dir")
    argl.append(ConfigManager.getInstance.getTorchRunLogDir)
    argl.append("--rdzv-backend")
    argl.append(torchRun.getRdzvBackend)
    if (torchRun.getRdzvEndpoint != null) {
      argl.append("--rdzv-endpoint")
      argl.append(torchRun.getRdzvEndpoint)
    }
    argl.append("--rdzv-id")
    argl.append(String.format("%s_%d", model.getModelName, port))
    if (torchRun.getMasterAddr != null) {
      argl.append("--master-addr")
      argl.append(torchRun.getMasterAddr)
      argl.append("--master-port")
      argl.append(String.valueOf(torchRun.getMasterPort))
    }
    argl.append("--max-restarts")
    argl.append(String.valueOf(1))
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
    val argl = new ListBuffer[String]
    val cppBackendBin = new File(workingDir, "ts/cpp/bin/model_worker_socket")
    val cppBackendLib = new File(workingDir, "ts/cpp/lib")
    if (!cppBackendBin.exists) throw new WorkerInitializationException("model_worker_socket not found")
    if (!cppBackendLib.exists) throw new WorkerInitializationException("model_worker cpp library not found")
    argl.append(cppBackendBin.getAbsolutePath)
    argl.append("--sock_type")
    argl.append(connector.getSocketType)
    argl.append(if (connector.isUds) "--sock_name"
    else "--port")
    argl.append(connector.getSocketPath)
    argl.append("--runtime_type")
    argl.append(runtimeType)
    argl.append("--model_dir")
    argl.append(modelPath.getAbsolutePath)
    if (ConfigManager.getInstance.getTsCppLogConfig != null) {
      argl.append("--logger_config_path")
      argl.append(ConfigManager.getInstance.getTsCppLogConfig)
    }
    argl.append("--metrics_config_path")
    argl.append(configManager.getMetricsConfigPath)
    val envp = EnvironmentUtils.getCppEnvString(cppBackendLib.getAbsolutePath)
    try {
      latch = new CountDownLatch(1)
      val args = argl.toArray() //new Array[String](argl.size))
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
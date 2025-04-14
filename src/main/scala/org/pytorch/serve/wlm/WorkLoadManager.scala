package org.pytorch.serve.wlm

import io.netty.channel.EventLoopGroup

import java.io.IOException
import java.net.HttpURLConnection
import java.util
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.pytorch.serve.snapshot.SnapshotManager
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.OSUtils
import org.pytorch.serve.wlm.{AsyncBatchAggregator, AsyncWorkerThread, ContinuousBatching, ModelVersionName, SequenceBatching, SequenceContinuousBatching, WorkerStateListener}
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.pytorch.serve.util.ConfigManager

import scala.jdk.CollectionConverters.*

object WorkLoadManager {
  private val logger = LoggerFactory.getLogger(classOf[WorkLoadManager])
}

class WorkLoadManager(private var configManager: ConfigManager, private var backendGroup: EventLoopGroup) {
  
  private var threadPool: ExecutorService =  Executors.newCachedThreadPool
  private var workers: ConcurrentHashMap[ModelVersionName, util.List[WorkerThread]] = new ConcurrentHashMap[ModelVersionName, util.List[WorkerThread]]
  private var port: AtomicInteger = new AtomicInteger(configManager.getInitialWorkerPort)
  private var distributionPort: AtomicInteger = new AtomicInteger(configManager.getInitialDistributionPort)
  private var gpuCounter: AtomicInteger = new AtomicInteger(0)

  def getWorkers(modelVersionName: ModelVersionName): util.List[WorkerThread] = {
    val list = workers.get(modelVersionName)
    if (list == null) return Collections.emptyList
    new util.ArrayList[WorkerThread](list)
  }

  def getWorkers: util.Map[Integer, WorkerThread] = {
    val map = new util.HashMap[Integer, WorkerThread]
//    import scala.collection.JavaConversions._
    for (workerThreads <- workers.values.asScala) {
//      import scala.collection.JavaConversions._
      for (worker <- workerThreads.asScala) {
        map.put(worker.getPid, worker)
      }
    }
    map
  }

  def hasNoWorker(modelVersionName: ModelVersionName): Boolean = {
    val worker = workers.get(modelVersionName)
    if (worker == null) return true
    worker.isEmpty
  }

  def getNumRunningWorkers(modelVersionName: ModelVersionName): Int = {
    var numWorking = 0
    val threads = workers.getOrDefault(modelVersionName, null)
    if (threads != null) {
//      import scala.collection.JavaConversions._
      for (thread <- threads.asScala) {
        if ((thread.getState ne WorkerState.WORKER_STOPPED) && (thread.getState ne WorkerState.WORKER_ERROR) && (thread.getState ne WorkerState.WORKER_SCALED_DOWN)) numWorking += 1
      }
    }
    numWorking
  }

  def getNumHealthyWorkers(modelVersionName: ModelVersionName): Int = {
    var numHealthy = 0
    val threads = workers.getOrDefault(modelVersionName, null)
    if (threads != null) {
//      import scala.collection.JavaConversions._
      for (thread <- threads.asScala) {
        if (thread.isHealthy) numHealthy += 1
      }
    }
    numHealthy
  }

  /**
   * Checks if cpu_launcher is enabled and currentWorkers > 0 (i.e., not initializing workers).
   * Workers are restarted so that when dynamically scaling the number of workers, cores that were
   * pinned to killed workers by the launcher are not left unutilizied. If isRestart, workers are
   * restarted to re-distribute cores that were pinned to killed workers to the remaining, alive
   * workers.
   */
  def isLauncherRestartWorkers(currentWorkers: Int): Boolean = configManager.isCPULauncherEnabled && currentWorkers > 0

  def modelChanged(model: Model, isStartup: Boolean, isCleanUp: Boolean): CompletableFuture[Integer] = model.getModelVersionName.synchronized {
    var isSnapshotSaved = false
    val future = new CompletableFuture[Integer]
    var minWorker = model.getMinWorkers
    var maxWorker = model.getMaxWorkers
    // Sets restartNumWorkers to the updated minWorker after scale up/down
    val restartNumWorkers = minWorker
    var threads: util.List[WorkerThread] = null
    if (minWorker == 0) {
      threads = workers.remove(model.getModelVersionName)
      if (threads == null) {
        future.complete(HttpURLConnection.HTTP_OK)
        if (!isStartup && !isCleanUp && !model.isWorkflowModel) SnapshotManager.getInstance.saveSnapshot()
        return future
      }
    }
    else threads = workers.computeIfAbsent(model.getModelVersionName, (k: ModelVersionName) => new util.ArrayList[WorkerThread])
    val currentWorkers = threads.size
    val isRestartWorkers = isLauncherRestartWorkers(currentWorkers)
    if (isRestartWorkers) {
      WorkLoadManager.logger.warn("removing {} current thread(s) prior to restarting {} thread(s)", currentWorkers, minWorker)
      // By setting maxWorker and minWorker to 0, removes all currentWorkers
      maxWorker = 0
      minWorker = 0
    }
    if (currentWorkers < minWorker) addThreads(threads, model, minWorker - currentWorkers, future)
    else {
      for (i <- currentWorkers - 1 to maxWorker by -1) {
        val thread = threads.remove(i)
        val lifecycle = thread.getLifeCycle
        thread.shutdown()
        val workerProcess = lifecycle.getProcess
        // Need to check worker process here since thread.shutdown() -> lifecycle.exit()
        // -> This may nullify process object per destroyForcibly doc.
        if (workerProcess != null && workerProcess.isAlive) {
          var workerDestroyed = false
          try {
            val cmd = String.format(OSUtils.getKillCmd, workerProcess.pid)
            val workerKillProcess = Runtime.getRuntime.exec(cmd, null, null)
            workerDestroyed = workerKillProcess.waitFor(configManager.getUnregisterModelTimeout, TimeUnit.SECONDS)
          } catch {
            case e@(_: InterruptedException | _: IOException) =>
              WorkLoadManager.logger.warn("WorkerThread interrupted during waitFor, possible async resource cleanup.")
              future.complete(HttpURLConnection.HTTP_INTERNAL_ERROR)
              return future
          }
          if (!workerDestroyed) {
            WorkLoadManager.logger.warn("WorkerThread timed out while cleaning, please resend request.")
            future.complete(HttpURLConnection.HTTP_CLIENT_TIMEOUT)
            return future
          }
        }
      }
      if (!isStartup && !isCleanUp && !model.isWorkflowModel) {
        SnapshotManager.getInstance.saveSnapshot()
        isSnapshotSaved = true
      }
      future.complete(HttpURLConnection.HTTP_OK)
    }
    // After removing all currentWorkers, add back (i.e., restart) restartNumWorkers
    if (isRestartWorkers) {
      WorkLoadManager.logger.warn("restarting {} thread(s)", restartNumWorkers)
      addThreads(threads, model, restartNumWorkers, future)
    }
    if (!isStartup && !isSnapshotSaved && !isCleanUp && !model.isWorkflowModel) SnapshotManager.getInstance.saveSnapshot()
    return future
  }

  private def addThreads(threads: util.List[WorkerThread], model: Model, count: Int, future: CompletableFuture[Integer]): Unit = {
    val listener = new WorkerStateListener(future, count)
    val maxGpu = model.getNumCores
    val stride = if (model.getParallelLevel > 0) model.getParallelLevel
    else 1
    for (i <- 0 until count) {
      var gpuId = -1
      if (maxGpu > 0) if (model.isHasCfgDeviceIds || model.getParallelLevel > 0) {
        gpuId = model.getGpuCounter.getAndAccumulate(stride, (prev: Int, myStride: Int) => (prev + myStride) % maxGpu)
        if (model.getParallelLevel == 0) gpuId = model.getDeviceIds.get(gpuId)
      }
      else gpuId = gpuCounter.accumulateAndGet(maxGpu, (prev: Int, maxGpuId: Int) => {
        
        var now = prev + 1; now
      } % maxGpuId)
      var aggregator: BatchAggregator = null
      if (model.isSequenceBatching && model.isContinuousBatching) aggregator = new SequenceContinuousBatching(model)
      else if (model.isSequenceBatching) aggregator = new SequenceBatching(model)
      else if (model.isContinuousBatching) aggregator = new ContinuousBatching(model)
      else if (model.isAsyncCommunication) aggregator = new AsyncBatchAggregator(model)
      else aggregator = new BatchAggregator(model)
      val currentPort = if (model.getParallelLevel > 0) if (configManager.isDebug) distributionPort.get
      else distributionPort.getAndAdd(model.getParallelLevel)
      else if (configManager.isDebug) port.get
      else port.getAndIncrement
      var thread: WorkerThread = null
      if (model.isAsyncCommunication) thread = new AsyncWorkerThread(configManager, backendGroup, currentPort, gpuId, model, aggregator, listener)
      else thread = new WorkerThread(configManager, backendGroup, currentPort, gpuId, model, aggregator, listener)
      threads.add(thread)
      threadPool.submit(thread)
    }
  }

  def scheduleAsync(r: Runnable): Unit = {
    threadPool.execute(r)
  }
}
package org.pytorch.serve.http.messages

import com.google.gson.{JsonObject, JsonSyntaxException}
import org.pytorch.serve.util.JsonUtils
import org.slf4j.{Logger, LoggerFactory}

import java.nio.charset.Charset
import java.util
import java.util.Date
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
object DescribeModelResponse {
  private val logger = LoggerFactory.getLogger(classOf[DescribeModelResponse])

  final class Worker {
    private var id: String = null
    private var startTime: Date = null
    private var status: String = null
    private var memoryUsage = 0L
    private var pid = 0
    private var gpu = false
    private var gpuUsage: String = null

    def getGpuUsage: String = gpuUsage

    def setGpuUsage(gpuUsage: String): Unit = {
      this.gpuUsage = gpuUsage
    }

    def getPid: Int = pid

    def setPid(pid: Int): Unit = {
      this.pid = pid
    }

    def getId: String = id

    def setId(id: String): Unit = {
      this.id = id
    }

    def getStartTime: Date = startTime

    def setStartTime(startTime: Date): Unit = {
      this.startTime = startTime
    }

    def getStatus: String = status

    def setStatus(status: String): Unit = {
      this.status = status
    }

    def isGpu: Boolean = gpu

    def setGpu(gpu: Boolean): Unit = {
      this.gpu = gpu
    }

    def getMemoryUsage: Long = memoryUsage

    def setMemoryUsage(memoryUsage: Long): Unit = {
      this.memoryUsage = memoryUsage
    }
  }

  final class Metrics {
    private var rejectedRequests = 0
    private var waitingQueueSize = 0
    private var requests = 0

    def getRejectedRequests: Int = rejectedRequests

    def setRejectedRequests(rejectedRequests: Int): Unit = {
      this.rejectedRequests = rejectedRequests
    }

    def getWaitingQueueSize: Int = waitingQueueSize

    def setWaitingQueueSize(waitingQueueSize: Int): Unit = {
      this.waitingQueueSize = waitingQueueSize
    }

    def getRequests: Int = requests

    def setRequests(requests: Int): Unit = {
      this.requests = requests
    }
  }

  final class JobQueueStatus {
    private var remainingCapacity = 0
    private var pendingRequests = 0

    def getRemainingCapacity: Int = remainingCapacity

    def setRemainingCapacity(remainingCapacity: Int): Unit = {
      this.remainingCapacity = remainingCapacity
    }

    def getPendingRequests: Int = pendingRequests

    def setPendingRequests(pendingRequests: Int): Unit = {
      this.pendingRequests = pendingRequests
    }
  }
}

class DescribeModelResponse {

  private var modelName: String = null
  private var modelVersion: String = null
  private var modelUrl: String = null
  private var engine: String = null
  private var runtime: String = null
  private var minWorkers = 0
  private var maxWorkers = 0
  private var batchSize = 0
  private var maxBatchDelay = 0
  private var responseTimeout = 0
  private var startupTimeout = 0
  private var maxRetryTimeoutInSec = 0L
  private var clientTimeoutInMills = 0L
  private var parallelType: String = null
  private var parallelLevel = 0
  private var deviceType: String = null
  private var deviceIds: ListBuffer[Int] = new ListBuffer[Int]
  private var continuousBatching = false
  private var useJobTicket = false
  private var useVenv = false
  private var stateful = false
  private var sequenceMaxIdleMSec = 0L
  private var sequenceTimeoutMSec = 0L
  private var maxNumSequence = 0
  private var maxSequenceJobQueueSize = 0
  private var status: String = null
  private var loadedAtStartup = false
  private var workers: ListBuffer[DescribeModelResponse.Worker] = new ListBuffer[DescribeModelResponse.Worker]
  private var metrics: DescribeModelResponse.Metrics = null
  private var jobQueueStatus: DescribeModelResponse.JobQueueStatus = null
  private var customizedMetadata: JsonObject = null

  def getModelName: String = modelName

  def setModelName(modelName: String): Unit = {
    this.modelName = modelName
  }

  def getLoadedAtStartup: Boolean = loadedAtStartup

  def setLoadedAtStartup(loadedAtStartup: Boolean): Unit = {
    this.loadedAtStartup = loadedAtStartup
  }

  def getModelVersion: String = modelVersion

  def setModelVersion(modelVersion: String): Unit = {
    this.modelVersion = modelVersion
  }

  def getModelUrl: String = modelUrl

  def setModelUrl(modelUrl: String): Unit = {
    this.modelUrl = modelUrl
  }

  def getEngine: String = engine

  def setEngine(engine: String): Unit = {
    this.engine = engine
  }

  def getRuntime: String = runtime

  def setRuntime(runtime: String): Unit = {
    this.runtime = runtime
  }

  def getMinWorkers: Int = minWorkers

  def setMinWorkers(minWorkers: Int): Unit = {
    this.minWorkers = minWorkers
  }

  def getMaxWorkers: Int = maxWorkers

  def setMaxWorkers(maxWorkers: Int): Unit = {
    this.maxWorkers = maxWorkers
  }

  def getBatchSize: Int = batchSize

  def setBatchSize(batchSize: Int): Unit = {
    this.batchSize = batchSize
  }

  def getMaxBatchDelay: Int = maxBatchDelay

  def setMaxBatchDelay(maxBatchDelay: Int): Unit = {
    this.maxBatchDelay = maxBatchDelay
  }

  def getResponseTimeout: Int = responseTimeout

  def getStartupTimeout: Int = startupTimeout

  def setResponseTimeout(responseTimeout: Int): Unit = {
    this.responseTimeout = responseTimeout
  }

  def setStartupTimeout(startupTimeout: Int): Unit = {
    this.startupTimeout = startupTimeout
  }

  def getMaxRetryTimeoutInSec: Long = maxRetryTimeoutInSec

  def setMaxRetryTimeoutInSec(maxRetryTimeoutInSec: Long): Unit = {
    this.maxRetryTimeoutInSec = maxRetryTimeoutInSec
  }

  def getClientTimeoutInMills: Long = clientTimeoutInMills

  def setClientTimeoutInMills(clientTimeoutInMills: Long): Unit = {
    this.clientTimeoutInMills = clientTimeoutInMills
  }

  def getParallelType: String = parallelType

  def setParallelType(parallelType: String): Unit = {
    this.parallelType = parallelType
  }

  def getParallelLevel: Int = parallelLevel

  def setParallelLevel(parallelLevel: Int): Unit = {
    this.parallelLevel = parallelLevel
  }

  def getDeviceType: String = deviceType

  def setDeviceType(deviceType: String): Unit = {
    this.deviceType = deviceType
  }

  def getDeviceIds: List[Int] = deviceIds.toList

  def setDeviceIds(deviceIds: List[Int]): Unit = {
    this.deviceIds.addAll(deviceIds)
  }

  def getContinuousBatching: Boolean = continuousBatching

  def setContinuousBatching(continuousBatching: Boolean): Unit = {
    this.continuousBatching = continuousBatching
  }

  def getUseJobTicket: Boolean = useJobTicket

  def setUseJobTicket(useJobTicket: Boolean): Unit = {
    this.useJobTicket = useJobTicket
  }

  def getUseVenv: Boolean = useVenv

  def setUseVenv(useVenv: Boolean): Unit = {
    this.useVenv = useVenv
  }

  def getStateful: Boolean = stateful

  def setStateful(stateful: Boolean): Unit = {
    this.stateful = stateful
  }

  def getSequenceMaxIdleMSec: Long = sequenceMaxIdleMSec

  def setSequenceMaxIdleMSec(sequenceMaxIdleMSec: Long): Unit = {
    this.sequenceMaxIdleMSec = sequenceMaxIdleMSec
  }

  def getSequenceTimeoutMSec: Long = sequenceTimeoutMSec

  def setSequenceTimeoutMSec(sequenceTimeoutMSec: Long): Unit = {
    this.sequenceTimeoutMSec = sequenceTimeoutMSec
  }

  def getMaxNumSequence: Int = maxNumSequence

  def setMaxNumSequence(maxNumSequence: Int): Unit = {
    this.maxNumSequence = maxNumSequence
  }

  def getMaxSequenceJobQueueSize: Int = maxSequenceJobQueueSize

  def setMaxSequenceJobQueueSize(maxSequenceJobQueueSize: Int): Unit = {
    this.maxSequenceJobQueueSize = maxSequenceJobQueueSize
  }

  def getStatus: String = status

  def setStatus(status: String): Unit = {
    this.status = status
  }

  def getWorkers: List[DescribeModelResponse.Worker] = workers.toList

  def setWorkers(workers: List[DescribeModelResponse.Worker]): Unit = {
    this.workers.addAll(workers)
  }

  def addWorker(id: String, startTime: Long, isRunning: Boolean, gpuId: Int, memoryUsage: Long, pid: Int, gpuUsage: String): Unit = {
    val worker = new DescribeModelResponse.Worker
    worker.setId(id)
    worker.setStartTime(new Date(startTime))
    worker.setStatus(if (isRunning) "READY"
    else "UNLOADING")
    worker.setMemoryUsage(memoryUsage)
    worker.setPid(pid)
    worker.setGpu(gpuId >= 0)
    worker.setGpuUsage(gpuUsage)
    workers.append(worker)
  }

  def getMetrics: DescribeModelResponse.Metrics = metrics

  def setMetrics(metrics: DescribeModelResponse.Metrics): Unit = {
    this.metrics = metrics
  }

  def getJobQueueStatus: DescribeModelResponse.JobQueueStatus = jobQueueStatus

  def setJobQueueStatus(jobQueueStatus: DescribeModelResponse.JobQueueStatus): Unit = {
    this.jobQueueStatus = jobQueueStatus
  }

  def setCustomizedMetadata(customizedMetadata: Array[Byte]): Unit = {
    val stringMetadata = new String(customizedMetadata, Charset.forName("UTF-8"))
    try this.customizedMetadata = JsonUtils.GSON.fromJson(stringMetadata, classOf[JsonObject])
    catch {
      case ex: JsonSyntaxException =>
        DescribeModelResponse.logger.warn("Customized metadata should be a dictionary.")
        this.customizedMetadata = new JsonObject
    }
  }

  def getCustomizedMetadata: JsonObject = customizedMetadata
}
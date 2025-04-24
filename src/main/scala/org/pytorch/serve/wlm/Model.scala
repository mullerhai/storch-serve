package org.pytorch.serve.wlm

import com.google.gson.{JsonElement, JsonObject}
import org.pytorch.serve.archive.model.ModelConfig.ParallelType.{NONE, PP, PPTP, TP}
import org.pytorch.serve.archive.model.{Manifest, ModelArchive, ModelConfig}
import org.pytorch.serve.archive.utils.ArchiveUtils
import org.pytorch.serve.job.{Job, JobGroup}
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.messages.WorkerCommands
import org.pytorch.serve.wlm.ModelVersionName
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import java.util
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap, LinkedBlockingDeque, TimeUnit}
import java.util.{Collections, Objects}
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, TreeMap, Map as MutableMap}
import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks.{break, breakable}

object Model {
  val DEFAULT_DATA_QUEUE = "DATA_QUEUE"
  val MIN_WORKERS = "minWorkers"
  val MAX_WORKERS = "maxWorkers"
  val BATCH_SIZE = "batchSize"
  val MAX_BATCH_DELAY = "maxBatchDelay"
  val RESPONSE_TIMEOUT = "responseTimeout"
  val STARTUP_TIMEOUT = "startupTimeout"
  val PARALLEL_LEVEL = "parallelLevel"
  val DEFAULT_VERSION = "defaultVersion"
  val MAR_NAME = "marName"
  val RUNTIME_TYPE = "runtimeType"
  private val logger = LoggerFactory.getLogger(classOf[Model])
}

class Model( var modelArchive: ModelArchive,var queueSize: Int) {
  private var jobsDb: ConcurrentMap[String, LinkedBlockingDeque[Job]] = null
  private var useJobTicket = false
  private var numJobTickets: AtomicInteger = null
  private var continuousBatching = false
  private var sequenceBatch = false
  private var asyncCommunication = false
  private var useVenv = false
  private var parallelLevel = 0
  private var maxRetryTimeoutInMill:Long = 5 * 60 * 1000
  private var clientTimeoutInMills = 0L
  private var parallelType = ModelConfig.ParallelType.NONE
  private var deviceType = if (ConfigManager.getInstance.getNumberOfGpu > 0) ModelConfig.DeviceType.GPU
  else ModelConfig.DeviceType.CPU
  private var deviceIds: ListBuffer[Int] = ListBuffer[Int]()
  private var numCores = 0
  private var lock: ReentrantLock = null
  private var jobGroupLock: ReentrantLock = null
  private var responseTimeout = 0
  private var startupTimeout = 0
  private var sequenceMaxIdleMSec = 0L
  private var sequenceTimeoutMSec = 0L
  private var maxNumSequence = 0
  private var maxSequenceJobQueueSize = 0
  private val stateful = false
  // key: seqId; value: SequenceJob
  private var jobGroups: ConcurrentMap[String, JobGroup] = null
  // store incoming new sequences' id
  private var pendingJobGroups: LinkedBlockingDeque[String] = null
  private var modelVersionName: ModelVersionName = null
  private val gpuCounter = new AtomicInteger(0)
  private var hasCfgDeviceIds = false
  var isWorkflowModel = false
  private var runtimeType: Manifest.RuntimeType = null
  // Total number of subsequent inference request failures
  private var failedInfReqs: AtomicInteger = null
  private var minWorkers = 0
  private var maxWorkers = 0
  private var batchSize = 0
  private var maxBatchDelay:Long = 0

  if (modelArchive != null && modelArchive.getModelConfig != null) {
    continuousBatching = modelArchive.getModelConfig.isContinuousBatching
    sequenceBatch = modelArchive.getModelConfig.isSequenceBatching
    asyncCommunication = modelArchive.getModelConfig.isAsyncCommunication
    useVenv = modelArchive.getModelConfig.getUseVenv
    if (modelArchive.getModelConfig.getParallelLevel > 0 && (modelArchive.getModelConfig.getParallelType ne ModelConfig.ParallelType.NONE)) {
      parallelLevel = modelArchive.getModelConfig.getParallelLevel
      parallelType = modelArchive.getModelConfig.getParallelType
    }
    if (modelArchive.getModelConfig.getDeviceType ne ModelConfig.DeviceType.NONE) deviceType = if ((modelArchive.getModelConfig.getDeviceType eq ModelConfig.DeviceType.GPU) && ConfigManager.getInstance.getNumberOfGpu > 0) ModelConfig.DeviceType.GPU
    else ModelConfig.DeviceType.CPU
    deviceIds.:++(modelArchive.getModelConfig.getDeviceIds)
    if (deviceIds != null && deviceIds.size > 0) {
      hasCfgDeviceIds = true
//      import scala.collection.JavaConversions._
      breakable(
        for (deviceId <- deviceIds) {
          if (deviceId < 0 || deviceId >= ConfigManager.getInstance.getNumberOfGpu) {
            Model.logger.warn("Invalid deviceId:{}, ignore deviceIds list", deviceId)
            deviceIds = null
            hasCfgDeviceIds = false
            break() 
            //todo: break is not supported
          }
        }
      )
    }
    maxRetryTimeoutInMill = modelArchive.getModelConfig.getMaxRetryTimeoutInSec * 1000
    clientTimeoutInMills = modelArchive.getModelConfig.getClientTimeoutInMills
    if (modelArchive.getModelConfig.getJobQueueSize > 0) {
      // overwrite the queueSize defined on config.property
      queueSize = modelArchive.getModelConfig.getJobQueueSize
    }
    useJobTicket = modelArchive.getModelConfig.isUseJobTicket
    if (modelArchive.getModelConfig.getSequenceMaxIdleMSec > 0) {
      sequenceMaxIdleMSec = modelArchive.getModelConfig.getSequenceMaxIdleMSec
      sequenceTimeoutMSec = modelArchive.getModelConfig.getSequenceTimeoutMSec
      maxSequenceJobQueueSize = modelArchive.getModelConfig.getMaxSequenceJobQueueSize
      maxNumSequence = Math.max(modelArchive.getModelConfig.getMaxNumSequence, batchSize * maxWorkers)
      if (sequenceBatch) {
        jobGroups = new ConcurrentHashMap[String, JobGroup](maxNumSequence)
        pendingJobGroups = new LinkedBlockingDeque[String](maxNumSequence)
        jobGroupLock = new ReentrantLock
      }
    }
  }
  else {
    batchSize = 1
    maxBatchDelay = 100
  }
  if (ConfigManager.getInstance.getNumberOfGpu > 0 && (deviceType ne ModelConfig.DeviceType.CPU)) numCores = if (hasCfgDeviceIds) deviceIds.size
  else ConfigManager.getInstance.getNumberOfGpu
  jobsDb = new ConcurrentHashMap[String, LinkedBlockingDeque[Job]]
  // Always have a queue for data
  jobsDb.putIfAbsent(Model.DEFAULT_DATA_QUEUE, new LinkedBlockingDeque[Job](queueSize))
  failedInfReqs = new AtomicInteger(0)
  numJobTickets = new AtomicInteger(0)
  lock = new ReentrantLock
  modelVersionName = new ModelVersionName(this.modelArchive.getModelName, this.modelArchive.getModelVersion)
  runtimeType = modelArchive.getManifest.getRuntime


  /**
   * The key can be categorized as 3 types 1) key: workerThreadId, value: managementAPI request 2)
   * key: DEFAULT_DATA_QUEUE, value: job queue for stateless model's inference request 3) key:
   * sequenceId, value: job queue for stateful model's sequence of inference requests
   */


  def getModelState(isDefaultVersion: Boolean): JsonObject = {
    val modelInfo = new JsonObject
    modelInfo.addProperty(Model.DEFAULT_VERSION, isDefaultVersion)
    modelInfo.addProperty(Model.MAR_NAME, ArchiveUtils.getFilenameFromUrl(getModelUrl))
    modelInfo.addProperty(Model.MIN_WORKERS, getMinWorkers)
    modelInfo.addProperty(Model.MAX_WORKERS, getMaxWorkers)
    modelInfo.addProperty(Model.BATCH_SIZE, getBatchSize)
    modelInfo.addProperty(Model.MAX_BATCH_DELAY, getMaxBatchDelay)
    modelInfo.addProperty(Model.RESPONSE_TIMEOUT, getResponseTimeout)
    modelInfo.addProperty(Model.STARTUP_TIMEOUT, getStartupTimeout)
    modelInfo.addProperty(Model.RUNTIME_TYPE, getRuntimeType.toString)
    if (parallelLevel > 0) modelInfo.addProperty(Model.PARALLEL_LEVEL, parallelLevel)
    modelInfo
  }

  def setModelState(modelInfo: JsonObject): Unit = {
    minWorkers = modelInfo.get(Model.MIN_WORKERS).getAsInt
    maxWorkers = modelInfo.get(Model.MAX_WORKERS).getAsInt
    maxBatchDelay = modelInfo.get(Model.MAX_BATCH_DELAY).getAsInt
    batchSize = modelInfo.get(Model.BATCH_SIZE).getAsInt
    responseTimeout = if (modelInfo.has(Model.RESPONSE_TIMEOUT) && !modelInfo.get(Model.RESPONSE_TIMEOUT).isJsonNull) modelInfo.get(Model.RESPONSE_TIMEOUT).getAsInt
    else ModelConfig.defaultResponseTimeout // default value for responseTimeout
    startupTimeout = if (modelInfo.has(Model.STARTUP_TIMEOUT) && !modelInfo.get(Model.STARTUP_TIMEOUT).isJsonNull) modelInfo.get(Model.STARTUP_TIMEOUT).getAsInt
    else ModelConfig.defaultStartupTimeout // default value for startupTimeout
    val runtime = modelInfo.get(Model.RUNTIME_TYPE)
    var runtime_str = Manifest.RuntimeType.PYTHON.toString
    if (runtime != null) runtime_str = runtime.getAsString
    runtimeType = Manifest.RuntimeType.valueOf(runtime_str)
    if (modelInfo.get(Model.PARALLEL_LEVEL) != null) parallelLevel = modelInfo.get(Model.PARALLEL_LEVEL).getAsInt
  }

  def getModelName: String = modelArchive.getModelName

  def getModelVersionName: ModelVersionName = modelVersionName

  def getVersion: String = modelArchive.getModelVersion

  def getModelDir: File = modelArchive.getModelDir

  def getModelUrl: String = modelArchive.getUrl

  def getModelArchive: ModelArchive = modelArchive

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

  def getMaxBatchDelay: Long = maxBatchDelay

  def setMaxBatchDelay(maxBatchDelay: Long): Unit = {
    this.maxBatchDelay = maxBatchDelay
  }

//  def isWorkflowModel: Boolean = isWorkflowModel

  def setWorkflowModel(workflowModelz: Boolean): Unit = {
    isWorkflowModel = workflowModelz
  }

  def getRuntimeType: Manifest.RuntimeType = this.runtimeType

  def setRuntimeType(runtimeType: Manifest.RuntimeType): Unit = {
    this.runtimeType = runtimeType
  }

  def addJob(threadId: String, job: Job): Unit = {
    var blockingDeque = jobsDb.get(threadId)
    if (blockingDeque == null) {
      blockingDeque = new LinkedBlockingDeque[Job]
      jobsDb.put(threadId, blockingDeque)
    }
    blockingDeque.offer(job)
  }

  def removeJobQueue(threadId: String): Unit = {
    if (!(threadId == Model.DEFAULT_DATA_QUEUE)) jobsDb.remove(threadId)
  }

  def addJob(job: Job): Boolean = {
    if (isUseJobTicket && !getJobTickets) {
      Model.logger.info("There are no job tickets available")
      return false
    }
    if (sequenceBatch && job.getGroupId != null) return addJobInGroup(job)
    jobsDb.get(Model.DEFAULT_DATA_QUEUE).offer(job)
  }

  private def addJobInGroup(job: Job): Boolean = try {
    jobGroupLock.lockInterruptibly()
    var jobGroup = jobGroups.get(job.getGroupId)
    if (jobGroup == null) if (jobGroups.size < maxNumSequence) {
      jobGroup = new JobGroup(job.getGroupId, maxSequenceJobQueueSize, sequenceTimeoutMSec)
      jobGroups.put(job.getGroupId, jobGroup)
      pendingJobGroups.offer(job.getGroupId)
      Model.logger.info("added jobGroup for sequenceId:{}", job.getGroupId)
    }
    else {
      Model.logger.warn("Skip the requestId: {} for sequence: {} due to jobGroups size: {} exceeding maxNumSequence: {}", job.getJobId, job.getGroupId, jobGroups.size, maxNumSequence)
      return false
    }
    jobGroup.appendJob(job)
  } catch {
    case e@(_: NullPointerException | _: InterruptedException) =>
      Model.logger.error("Skip the requestId: {} for sequence: {} due to exception", job.getJobId, job.getGroupId, e)
      false
  } finally if (jobGroupLock.isHeldByCurrentThread) jobGroupLock.unlock()

  def addFirst(job: Job): Unit = {
    jobsDb.get(Model.DEFAULT_DATA_QUEUE).addFirst(job)
  }

  @throws[InterruptedException]
  def pollMgmtJob(threadId: String, waitTime: Long, jobsRepo: mutable.Map[String, Job]): Boolean = {
    if (jobsRepo == null || threadId == null || threadId.isEmpty) throw new IllegalArgumentException("Invalid input given provided")
    if (!jobsRepo.isEmpty) throw new IllegalArgumentException("The jobs repo provided contains stale jobs. Clear them!!")
    val jobsQueue = jobsDb.get(threadId)
    if (jobsQueue != null && !jobsQueue.isEmpty) {
      val j = jobsQueue.poll(waitTime, TimeUnit.MILLISECONDS)
      if (j != null) {
        jobsRepo.+=(j.getJobId -> j)
        return true
      }
    }
    false
  }

  @throws[InterruptedException]
  def pollInferJob(jobsRepo: mutable.Map[String, Job], batchSize: Int): Unit = {
    var jobsQueue: LinkedBlockingDeque[Job] = null
    try {
      if (isUseJobTicket) incNumJobTickets
      lock.lockInterruptibly()
      jobsQueue = jobsDb.get(Model.DEFAULT_DATA_QUEUE)
      pollInferJob(jobsRepo, batchSize, jobsQueue)
    } finally if (lock.isHeldByCurrentThread) lock.unlock()
  }

  @throws[InterruptedException]
  def pollInferJob(jobsRepo: mutable.Map[String, Job], batchSizez: Int, jobsQueue: LinkedBlockingDeque[Job]): Unit = {
    var batchSize = batchSizez
    val pollNoWait = if (jobsRepo.isEmpty) false
    else true
    var maxDelay = maxBatchDelay
    var j: Job = null
    if (jobsRepo.isEmpty) {
      j = jobsQueue.poll(Long.MaxValue, TimeUnit.MILLISECONDS)
      Model.logger.trace("get first job: {}", Objects.requireNonNull(j).getJobId)
      jobsRepo.+=(j.getJobId -> j)
      // batch size always is 1 for describe request job
      if (j.getCmd eq WorkerCommands.DESCRIBE) if (jobsRepo.isEmpty) {
        jobsRepo.+=(j.getJobId -> j)
        return
      }
      else {
        jobsQueue.addFirst(j)
        return
      }
    }
    var begin = System.currentTimeMillis
    batchSize = if (pollNoWait) then batchSize else batchSize - 1
    breakable(
      for (i <- 0 until batchSize) {
        if (pollNoWait) j = jobsQueue.poll
        else j = jobsQueue.poll(maxDelay, TimeUnit.MILLISECONDS)
        if (j == null) break() //todo: break is not supported
        val end = System.currentTimeMillis
        // job batch size always is 1 when request is describe prediction
        if (j.getCmd eq WorkerCommands.DESCRIBE) {
          // Add the job back into the jobsQueue
          jobsQueue.addFirst(j)
          break()  //todo: break is not supported
        }
        maxDelay -= (end - begin)
        begin = end
        if (j.getPayload.getClientExpireTS > System.currentTimeMillis) then jobsRepo.+=(j.getJobId -> j)
        else Model.logger.warn("Drop inference request {} due to client timeout", j.getPayload.getRequestId)
        if (maxDelay <= 0) break //todo: break is not supported
      }
    )

    Model.logger.trace("sending jobs, size: {}", jobsRepo.size)
  }

  @throws[InterruptedException]
  def pollBatch(threadId: String, waitTime: Long, jobsRepo: mutable.Map[String, Job]): Unit = {
    if (jobsRepo == null || threadId == null || threadId.isEmpty) throw new IllegalArgumentException("Invalid input given provided")
    if (!jobsRepo.isEmpty) throw new IllegalArgumentException("The jobs repo provided contains stale jobs. Clear them!!")
    var jobsQueue = jobsDb.get(threadId)
    if (jobsQueue != null && !jobsQueue.isEmpty) {
      val j = jobsQueue.poll(waitTime, TimeUnit.MILLISECONDS)
      if (j != null) {
        jobsRepo.put(j.getJobId, j)
        return
      }
    }
    try {
      if (isUseJobTicket) incNumJobTickets
      lock.lockInterruptibly()
      var maxDelay = maxBatchDelay
      jobsQueue = jobsDb.get(Model.DEFAULT_DATA_QUEUE)
      var j = jobsQueue.poll(Long.MaxValue, TimeUnit.MILLISECONDS)
      Model.logger.trace("get first job: {}", Objects.requireNonNull(j).getJobId)
      jobsRepo.put(j.getJobId, j)
      // batch size always is 1 for describe request job
      if (j.getCmd eq WorkerCommands.DESCRIBE) return
      var begin = System.currentTimeMillis
      breakable(
        for (i <- 0 until batchSize - 1) {
          j = jobsQueue.poll(maxDelay, TimeUnit.MILLISECONDS)
          if (j == null) break() //todo: break is not supported
          val end = System.currentTimeMillis
          // job batch size always is 1 when request is describe
          if (j.getCmd eq WorkerCommands.DESCRIBE) {
            // Add the job back into the jobsQueue
            jobsQueue.addFirst(j)
            break() //todo: break is not supported
          }
          maxDelay -= end - begin
          begin = end
          if (j.getPayload.getClientExpireTS > System.currentTimeMillis) jobsRepo.put(j.getJobId, j)
          else Model.logger.warn("Drop inference request {} due to client timeout", j.getPayload.getRequestId)
          if (maxDelay <= 0) break //todo: break is not supported
        }
      )
  
      Model.logger.trace("sending jobs, size: {}", jobsRepo.size)
    } finally if (lock.isHeldByCurrentThread) lock.unlock()
  }

  def getJobQueueRemainingCapacity: Int = {
    val jobsQueue = jobsDb.get(Model.DEFAULT_DATA_QUEUE)
    if (jobsQueue != null) return jobsQueue.remainingCapacity
    0
  }

  def getPendingRequestsInJobQueue: Int = {
    val jobsQueue = jobsDb.get(Model.DEFAULT_DATA_QUEUE)
    if (jobsQueue != null) return jobsQueue.size
    0
  }

  def incrFailedInfReqs: Int = failedInfReqs.incrementAndGet

  def resetFailedInfReqs(): Unit = {
    failedInfReqs.set(0)
  }

  def getResponseTimeout: Int = if (ConfigManager.getInstance.isDebug) Integer.MAX_VALUE
  else responseTimeout

  def getStartupTimeout: Int = if (ConfigManager.getInstance.isDebug) Integer.MAX_VALUE
  else startupTimeout

  def setResponseTimeout(responseTimeout: Int): Unit = {
    this.responseTimeout = responseTimeout
  }

  def setStartupTimeout(startupTimeout: Int): Unit = {
    this.startupTimeout = startupTimeout
  }

  def getDeviceIds: List[Int] = this.deviceIds.toList

  def setDeviceIds(deviceIds: List[Int]): Unit = {
    this.deviceIds.addAll(deviceIds)
    //    Collections.copy(this.deviceIds, deviceIds)
  }

  def getParallelLevel: Int = this.parallelLevel

  def getParallelType: ModelConfig.ParallelType = this.parallelType

  def getDeviceType: ModelConfig.DeviceType = this.deviceType

  def getNumCores: Int = this.numCores

  def getGpuCounter: AtomicInteger = gpuCounter

  def isHasCfgDeviceIds: Boolean = hasCfgDeviceIds

  def getMaxRetryTimeoutInMill: Long = maxRetryTimeoutInMill

  def setMaxRetryTimeoutInMill(maxRetryTimeoutInMill: Long): Unit = {
    this.maxRetryTimeoutInMill = maxRetryTimeoutInMill
  }

  def getClientTimeoutInMills: Long = clientTimeoutInMills

  def setClientTimeoutInMills(clientTimeoutInMills: Long): Unit = {
    this.clientTimeoutInMills = clientTimeoutInMills
  }

  def isUseJobTicket: Boolean = useJobTicket

  def incNumJobTickets: Int = this.numJobTickets.incrementAndGet

  def decNumJobTickets: Int = this.numJobTickets.decrementAndGet

  def getJobTickets: Boolean = {
    if (this.numJobTickets.get == 0) return false
    this.numJobTickets.decrementAndGet
    true
  }

  def getSequenceMaxIdleMSec: Long = sequenceMaxIdleMSec

  def setSequenceMaxIdleMSec(sequenceMaxIdleMSec: Long): Unit = {
    this.sequenceMaxIdleMSec = sequenceMaxIdleMSec
  }

  def getSequenceTimeoutMSec: Long = sequenceTimeoutMSec

  def setSequenceTimeoutMSec(sequenceTimeoutMSec: Long): Unit = {
    this.sequenceTimeoutMSec = sequenceTimeoutMSec
  }

  def getMaxSequenceJobQueueSize: Int = maxSequenceJobQueueSize

  def getMaxNumSequence: Int = maxNumSequence

  def getPendingJobGroups: LinkedBlockingDeque[String] = pendingJobGroups

  def getJobGroup(groupId: String): JobGroup = jobGroups.get(groupId)

  def removeJobGroup(groupId: String): Unit = {
    jobGroups.remove(groupId)
  }

  def isContinuousBatching: Boolean = continuousBatching

  def isSequenceBatching: Boolean = sequenceBatch

  def isAsyncCommunication: Boolean = asyncCommunication

  def isUseVenv: Boolean = if (getRuntimeType eq Manifest.RuntimeType.PYTHON) useVenv
  else false

  def hasTensorParallel: Boolean = this.parallelType match {
    case PP => false
    case NONE => false
    case _ =>
      true
  }
}
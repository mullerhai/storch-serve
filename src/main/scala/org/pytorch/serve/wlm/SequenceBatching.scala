package org.pytorch.serve.wlm

import org.pytorch.serve.job.{Job, JobGroup}
import org.pytorch.serve.wlm.Model

import java.time.{Duration, Instant}
import java.util
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
//import org.pytorch.serve.servingsdk.Model
import org.pytorch.serve.util.messages.{BaseModelRequest, ModelWorkerResponse}
import org.pytorch.serve.wlm.BatchAggregator
import org.slf4j.{Logger, LoggerFactory}

import scala.jdk.CollectionConverters.*

object SequenceBatching {
  private val logger = LoggerFactory.getLogger(classOf[SequenceBatching])
}

class SequenceBatching(model: Model) extends BatchAggregator(model) {
  protected var eventJobGroupIds: LinkedBlockingDeque[String] = new LinkedBlockingDeque[String]
  // A queue holds jobs ready for this aggregator to add into a batch. Each job of this queue is
  // from distinct jobGroup. jobs
  protected var jobsQueue: LinkedBlockingDeque[Job] = new LinkedBlockingDeque[Job]
  private var eventDispatcher: Thread = new Thread(new EventDispatcher)
  private var isPollJobGroup: AtomicBoolean = new AtomicBoolean(false)
  // A list of jobGroupIds which are added into current batch. These jobGroupIds need to be added
  // back to eventJobGroupIds once their jobs are processed by a batch.
  protected var currentJobGroupIds: ListBuffer[String] = new ListBuffer[String]

  private val running = new AtomicBoolean(true)
  private var localCapacity = Math.max(1, model.getMaxNumSequence / model.getMinWorkers)
//  this.currentJobGroupIds = new util.LinkedList[String]
  private var pollExecutors: ExecutorService = Executors.newFixedThreadPool(model.getBatchSize + 1)
//  this.jobsQueue = new LinkedBlockingDeque[Job]
//  this.isPollJobGroup = new AtomicBoolean(false)
//  this.eventJobGroupIds = new LinkedBlockingDeque[String]
  this.eventJobGroupIds.add("")
//  this.eventDispatcher = new Thread(new SequenceBatching#EventDispatcher)
  this.eventDispatcher.start()
  
  /**
   * eventJobGroupIds is an queue in EventDispatcher. It's item has 2 cases. 1) empty string:
   * trigger EventDispatcher to fetch new job groups. 2) job group id: trigger EventDispatcher to
   * fetch a new job from this jobGroup.
   */


  override def startEventDispatcher(): Unit = {
    this.eventDispatcher.start()
  }

  def stopEventDispatcher(): Unit = {
    this.eventDispatcher.interrupt()
  }

  override def sendResponse(message: ModelWorkerResponse): Boolean = {
    val jobDone = super.sendResponse(message)
    if (jobDone && currentJobGroupIds.nonEmpty) {
      eventJobGroupIds.addAll(currentJobGroupIds.asJava)
      currentJobGroupIds.clear()
    }
    jobDone
  }

  override def sendError(message: BaseModelRequest, error: String, status: Int): Unit = {
    super.sendError(message, error, status)
    if (currentJobGroupIds.nonEmpty) {
      eventJobGroupIds.addAll(currentJobGroupIds.asJava)
      currentJobGroupIds.clear()
    }
  }

  /**
   * The priority of polling a batch P0: poll a job with one single management request. In this
   * case, the batchSize is 1. P1: poll jobs from job groups. In this case, the batch size is
   * equal to or less than the number of job groups storeed in this aggregator. P2: poll jobs from
   * the DEFAULT_DATA_QUEUE of this model.
   */
  @throws[InterruptedException]
  @throws[ExecutionException]
  override def pollBatch(threadName: String, state: WorkerState): Unit = {
    var pollMgmtJobStatus = false
    if (jobs.isEmpty) pollMgmtJobStatus = model.pollMgmtJob(threadName, if (state eq WorkerState.WORKER_MODEL_LOADED) 0
    else Long.MaxValue, jobs)
    if (!pollMgmtJobStatus && (state eq WorkerState.WORKER_MODEL_LOADED)) pollInferJob()
  }

  protected def cleanJobGroup(jobGroupId: String): Unit = {
    SequenceBatching.logger.debug("Clean jobGroup: {}", jobGroupId)
    if (jobGroupId != null) model.removeJobGroup(jobGroupId)
  }

  override def handleErrorJob(job: Job): Unit = {
    if (job.getGroupId == null) model.addFirst(job)
    else SequenceBatching.logger.error("Failed to process requestId: {}, sequenceId: {}", job.getPayload.getRequestId, job.getGroupId)
  }

  override def cleanJobs(): Unit = {
    super.cleanJobs()
    if (currentJobGroupIds.nonEmpty) {
      eventJobGroupIds.addAll(currentJobGroupIds.asJava)
      currentJobGroupIds.clear()
    }
  }

  @throws[InterruptedException]
  protected def pollInferJob(): Unit = {
    model.pollInferJob(jobs, model.getBatchSize, jobsQueue)
    //    import scala.collection.JavaConversions._
    for (job <- jobs.values) {
      if (job.getGroupId != null) currentJobGroupIds.append(job.getGroupId)
    }
  }

  @throws[InterruptedException]
  private def pollJobGroup(): Unit = {
    if (isPollJobGroup.getAndSet(true)) return
    val tmpJobGroups = new mutable.LinkedHashSet[String]
    val jobGroupId = model.getPendingJobGroups.poll(Long.MaxValue, TimeUnit.MILLISECONDS)
    if (jobGroupId != null) {
      addJobGroup(jobGroupId)
      val quota = Math.min(this.localCapacity - jobsQueue.size, Math.max(1, model.getPendingJobGroups.size / model.getMaxWorkers))
      if (quota > 0 && model.getPendingJobGroups.size > 0) model.getPendingJobGroups.drainTo(tmpJobGroups.asJava, quota)
      for (jGroupId <- tmpJobGroups) {
        addJobGroup(jGroupId)
      }
    }
    isPollJobGroup.set(false)
  }

  override def shutdown(): Unit = {
    this.setRunning(false)
    this.shutdownExecutors()
    this.stopEventDispatcher()
  }

  def shutdownExecutors(): Unit = {
    this.pollExecutors.shutdown()
  }

  private def addJobGroup(jobGroupId: String): Unit = {
    if (jobGroupId != null) eventJobGroupIds.add(jobGroupId)
  }

  def setRunning(running: Boolean): Unit = {
    this.running.set(running)
  }

  private[wlm] class EventDispatcher extends Runnable {
    override def run(): Unit = {
      while (running.get) try {
        val jobGroupId = eventJobGroupIds.poll(model.getMaxBatchDelay, TimeUnit.MILLISECONDS)
        if (jobGroupId == null || jobGroupId.isEmpty) CompletableFuture.runAsync(() => {
          try pollJobGroup()
          catch {
            case e: InterruptedException =>
              SequenceBatching.logger.error("Failed to poll a job group", e)
          }
        }, pollExecutors)
        else CompletableFuture.runAsync(() => {
          pollJobFromJobGroup(jobGroupId)
        }, pollExecutors)
      } catch {
        case e: InterruptedException =>
          if (running.get) SequenceBatching.logger.error("EventDispatcher failed to get jobGroup", e)
      }
    }

    private def pollJobFromJobGroup(jobGroupId: String): Unit = {
      // Poll a job from a jobGroup
      val jobGroup = model.getJobGroup(jobGroupId)
      var job: Job = null
      val isPolling = jobGroup.getPolling
      if (!jobGroup.isFinished) if (!isPolling.getAndSet(true)) {
        job = jobGroup.pollJob(getPollJobGroupTimeoutMSec(jobGroup))
        isPolling.set(false)
      }
      else return
      if (job == null) {
        // JobGroup expired, clean it.
        cleanJobGroup(jobGroupId)
        // intent to add new job groups.
        eventJobGroupIds.add("")
      }
      else jobsQueue.add(job)
    }

    private def getPollJobGroupTimeoutMSec(jobGroup: JobGroup) = {
      var pollTimeout = 0l
      val currentTimestamp = Instant.now
      val expiryTimestamp = jobGroup.getExpiryTimestamp
      if (expiryTimestamp eq Instant.MAX) pollTimeout = model.getSequenceMaxIdleMSec
      else if (currentTimestamp.isBefore(expiryTimestamp)) {
        val remainingPollDuration = Duration.between(currentTimestamp, expiryTimestamp).toMillis
        pollTimeout = Math.min(model.getSequenceMaxIdleMSec, remainingPollDuration)
      }
      pollTimeout
    }
  }
}
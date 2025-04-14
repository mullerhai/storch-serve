package org.pytorch.serve.job

import java.time.Instant
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object JobGroup {
  private val logger = LoggerFactory.getLogger(classOf[JobGroup])
}

class JobGroup( var groupId: String,  var maxJobQueueSize: Int, sequenceTimeoutMSec: Long) {

  private[job] var jobs: LinkedBlockingDeque[Job] = new LinkedBlockingDeque[Job](maxJobQueueSize)
  private[job] var finished: AtomicBoolean = new AtomicBoolean(false)
  private[job] var polling: AtomicBoolean = new AtomicBoolean(false)
  private[job] var expiryTimestamp: Instant = if (sequenceTimeoutMSec > 0) Instant.now.plusMillis(sequenceTimeoutMSec)
  else Instant.MAX

  def appendJob(job: Job): Boolean = jobs.offer(job)

  def pollJob(timeout: Long): Job = {
    if (finished.get) return null
    try return jobs.poll(timeout, TimeUnit.MILLISECONDS)
    catch {
      case e: InterruptedException =>
        JobGroup.logger.error("Failed to poll a job from group {}", groupId, e)
    }
    null
  }

  def getGroupId: String = groupId

  def setFinished(sequenceEnd: Boolean): Unit = {
    this.finished.set(sequenceEnd)
  }

  def isFinished: Boolean = this.finished.get

  def getPolling: AtomicBoolean = this.polling

  def getExpiryTimestamp: Instant = this.expiryTimestamp
}
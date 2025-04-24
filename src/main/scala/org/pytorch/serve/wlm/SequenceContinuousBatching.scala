package org.pytorch.serve.wlm

import org.pytorch.serve.job.{Job, JobGroup}
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.messages.*
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.concurrent.ExecutionException
import scala.jdk.CollectionConverters.*

object SequenceContinuousBatching {
  private val logger = LoggerFactory.getLogger(classOf[SequenceContinuousBatching])
}

class SequenceContinuousBatching(model: Model) extends SequenceBatching(model) {
  @throws[InterruptedException]
  @throws[ExecutionException]
  override def getRequest(threadName: String, state: WorkerState): BaseModelRequest = {
    val req = new ModelInferenceRequest(model.getModelName)
    pollBatch(threadName, state)
    if (model.isUseJobTicket && jobs.isEmpty) {
      model.decNumJobTickets
      return req
    }
    for (j <- jobs.values) {
      if (j.isControlCmd) {
        if (jobs.size > 1) throw new IllegalStateException("Received more than 1 control command. " + "Control messages should be processed/retrieved one at a time.")
        val input = j.getPayload
        var gpuId = -1
        val gpu = input.getStringParameter("gpu")
        if (gpu != null) gpuId = gpu.toInt
        return new ModelLoadModelRequest(model, gpuId)
      }
      else {
        req.setCommand(j.getCmd)
        j.setScheduled()
        req.addRequest(j.getPayload)
      }
    }
    req
  }

  /**
   * @param message : a response of a batch inference requests
   * @return - true: either a non-stream response or last stream response is sent - false: a
   *         stream response (not include the last stream) is sent This is a copy of sendResponse from
   *         ContinuousBatching + 1. setJobGroupFinished: handle a list of jobGroups end. 2.
   *         resetCurrentJobGroupIds
   */
  override def sendResponse(message: ModelWorkerResponse): Boolean = {
    // TODO: Handle prediction level code
    if (message.getCode == 200) {
      if (message.getPredictions.isEmpty) {
        // The jobs size is always 1 in the case control command

        for (j <- jobs.toSeq) {
          val job = j._2
          if (job.isControlCmd) {
            cleanJobs()
            return true
          }
        }
      }

      for (prediction <- message.getPredictions) {
        val jobId = prediction.getRequestId
        val job = jobs.get(jobId)
        if (job.isEmpty) throw new IllegalStateException("Unexpected job in sendResponse() with 200 status code: " + jobId)
        if (job.get.getPayload.getClientExpireTS > System.currentTimeMillis) job.get.response(prediction.getResp, prediction.getContentType, prediction.getStatusCode, prediction.getReasonPhrase, prediction.getHeaders)
        else SequenceContinuousBatching.logger.warn("Drop response for inference request {} due to client timeout", job.get.getPayload.getRequestId)
        val streamNext = prediction.getHeaders.get(org.pytorch.serve.util.messages.RequestInput.TS_STREAM_NEXT)
        if (streamNext.isEmpty || (streamNext.isDefined && streamNext.get == "false")) jobs.remove(jobId)
        else if (!job.get.isOpen) {
          jobs.remove(job.get.getJobId)
          SequenceContinuousBatching.logger.info("Connection to client got closed; Removing job: {}", job.get.getPayload.getRequestId)
        }
        else job.get.getPayload.setCachedInBackend(true)
        setJobGroupFinished(prediction)
      }
    }
    else {

      for (j <- jobs.toSeq) {
        if (j._2 == null) throw new IllegalStateException("Unexpected job in sendResponse() with non 200 status code: " + j._1)
        val job = j._2
        if (job.getPayload.getClientExpireTS > System.currentTimeMillis) job.sendError(message.getCode, message.getMessage)
        else SequenceContinuousBatching.logger.warn("Drop error response for inference request {} due to client timeout", job.getPayload.getRequestId)
      }
      cleanJobs()
    }
    resetCurrentJobGroupIds()
    true
  }

  private def setJobGroupFinished(prediction: Predictions): Unit = {
    val pre = prediction.getHeaders.getOrElse(ConfigManager.getInstance.getTsHeaderKeySequenceEnd, null)
    if (pre != null) {
      val jobGroupIds = pre.split(";")
      for (j <- jobGroupIds) {
        val jobGroupId = j.trim
        val jobGroup = model.getJobGroup(jobGroupId)
        if (jobGroup != null) {
          jobGroup.setFinished(true)
          // JobGroup finished, clean it.
          cleanJobGroup(jobGroupId)
          // intent to add new job groups.
          eventJobGroupIds.add("")
        }
      }
    }
  }

  private def resetCurrentJobGroupIds(): Unit = {
    if (!currentJobGroupIds.isEmpty) {
      eventJobGroupIds.addAll(currentJobGroupIds.asJava)
      currentJobGroupIds.clear()
    }
  }

  @throws[InterruptedException]
  override protected def pollInferJob(): Unit = {
    // TBD: Temporarily hard code the continuous batch size is 2 * batchSize
    model.pollInferJob(jobs, model.getBatchSize * 2 - jobs.size, jobsQueue)
//    import scala.collection.JavaConversions._
    for (job <- jobs.values) {
      if (job.getGroupId != null) currentJobGroupIds.append(job.getGroupId)
    }
  }
}
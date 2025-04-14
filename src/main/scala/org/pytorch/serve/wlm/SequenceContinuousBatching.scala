package org.pytorch.serve.wlm

import java.util
import java.util.concurrent.ExecutionException
import org.pytorch.serve.job.Job
import org.pytorch.serve.job.JobGroup
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.messages.BaseModelRequest
import org.pytorch.serve.util.messages.ModelInferenceRequest
import org.pytorch.serve.util.messages.ModelLoadModelRequest
import org.pytorch.serve.util.messages.ModelWorkerResponse
import org.pytorch.serve.util.messages.Predictions
import org.pytorch.serve.util.messages.RequestInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import  org.pytorch.serve.util.messages.Predictions

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
//    import scala.collection.JavaConversions._
    for (j <- jobs.values.asScala) {
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
//        import scala.collection.JavaConversions._
        for (j <- jobs.entrySet.asScala) {
          val job = j.getValue
          if (job.isControlCmd) {
            cleanJobs()
            return true
          }
        }
      }
//      import scala.collection.JavaConversions._
      for (prediction <- message.getPredictions.asScala) {
        val jobId = prediction.getRequestId
        val job = jobs.get(jobId)
        if (job == null) throw new IllegalStateException("Unexpected job in sendResponse() with 200 status code: " + jobId)
        if (job.getPayload.getClientExpireTS > System.currentTimeMillis) job.response(prediction.getResp, prediction.getContentType, prediction.getStatusCode, prediction.getReasonPhrase, prediction.getHeaders)
        else SequenceContinuousBatching.logger.warn("Drop response for inference request {} due to client timeout", job.getPayload.getRequestId)
        val streamNext = prediction.getHeaders.get(org.pytorch.serve.util.messages.RequestInput.TS_STREAM_NEXT)
        if (streamNext == null || (streamNext != null && streamNext == "false")) jobs.remove(jobId)
        else if (!job.isOpen) {
          jobs.remove(job.getJobId)
          SequenceContinuousBatching.logger.info("Connection to client got closed; Removing job: {}", job.getPayload.getRequestId)
        }
        else job.getPayload.setCachedInBackend(true)
        setJobGroupFinished(prediction)
      }
    }
    else {
//      import scala.collection.JavaConversions._
      for (j <- jobs.entrySet.asScala) {
        if (j.getValue == null) throw new IllegalStateException("Unexpected job in sendResponse() with non 200 status code: " + j.getKey)
        val job = j.getValue
        if (job.getPayload.getClientExpireTS > System.currentTimeMillis) job.sendError(message.getCode, message.getMessage)
        else SequenceContinuousBatching.logger.warn("Drop error response for inference request {} due to client timeout", job.getPayload.getRequestId)
      }
      cleanJobs()
    }
    resetCurrentJobGroupIds()
    true
  }

  private def setJobGroupFinished(prediction: Predictions): Unit = {
    val pre = prediction.getHeaders.getOrDefault(ConfigManager.getInstance.getTsHeaderKeySequenceEnd, null)
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

  @throws[InterruptedException]
  override protected def pollInferJob(): Unit = {
    // TBD: Temporarily hard code the continuous batch size is 2 * batchSize
    model.pollInferJob(jobs, model.getBatchSize * 2 - jobs.size, jobsQueue)
//    import scala.collection.JavaConversions._
    for (job <- jobs.values.asScala) {
      if (job.getGroupId != null) currentJobGroupIds.add(job.getGroupId)
    }
  }

  private def resetCurrentJobGroupIds(): Unit = {
    if (!currentJobGroupIds.isEmpty) {
      eventJobGroupIds.addAll(currentJobGroupIds)
      currentJobGroupIds.clear()
    }
  }
}
package org.pytorch.serve.wlm

import org.pytorch.serve.job.Job
import org.pytorch.serve.util.messages.*
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.concurrent.ExecutionException
import scala.jdk.CollectionConverters.*
object ContinuousBatching {
  private val logger = LoggerFactory.getLogger(classOf[ContinuousBatching])
}

class ContinuousBatching(model: Model) extends BatchAggregator(model) {
  @throws[InterruptedException]
  @throws[ExecutionException]
  override def getRequest(threadName: String, state: WorkerState): BaseModelRequest = {
    val batchQuota = model.getBatchSize - jobs.size
    val req = new ModelInferenceRequest(model.getModelName)
    pollBatch(threadName, state, batchQuota)
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
   *         stream response (not include the last stream) is sent
   */
  override def sendResponse(message: ModelWorkerResponse): Boolean = {
    // TODO: Handle prediction level code
    if (message.getCode == 200) {
      if (message.getPredictions.isEmpty) {
        // The jobs size is always 1 in the case control command

        for (j <- jobs.toList) {
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
        if (job == null) throw new IllegalStateException("Unexpected job in sendResponse() with 200 status code: " + jobId)
        if (job.get.getPayload.getClientExpireTS > System.currentTimeMillis) job.get.response(prediction.getResp, prediction.getContentType, prediction.getStatusCode, prediction.getReasonPhrase, prediction.getHeaders)
        else ContinuousBatching.logger.warn("Drop response for inference request {} due to client timeout", job.get.getPayload.getRequestId)
        val streamNext = prediction.getHeaders.get(org.pytorch.serve.util.messages.RequestInput.TS_STREAM_NEXT)
        if (streamNext == null || (streamNext.isDefined && streamNext.get == "false")) jobs.remove(jobId)
        else if (!job.get.isOpen) {
          jobs.remove(job.get.getJobId)
          ContinuousBatching.logger.info("Connection to client got closed; Removing job: {}", job.get.getPayload.getRequestId)
        }
        else job.get.getPayload.setCachedInBackend(true)
      }
    }
    else {

      for (j <- jobs.toList) {
        if (j._2 == null) throw new IllegalStateException("Unexpected job in sendResponse() with non 200 status code: " + j._1)
        val job = j._2
        if (job.getPayload.getClientExpireTS > System.currentTimeMillis) job.sendError(message.getCode, message.getMessage)
        else ContinuousBatching.logger.warn("Drop error response for inference request {} due to client timeout", job.getPayload.getRequestId)
      }
      cleanJobs()
    }
    true
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  private def pollBatch(threadName: String, state: WorkerState, batchSize: Int): Unit = {
    var pollMgmtJobStatus = false
    if (jobs.isEmpty) pollMgmtJobStatus = model.pollMgmtJob(threadName, if (state eq WorkerState.WORKER_MODEL_LOADED) 0
    else Long.MaxValue, jobs)
    if (!pollMgmtJobStatus && (state eq WorkerState.WORKER_MODEL_LOADED)) model.pollInferJob(jobs, batchSize)
  }
}
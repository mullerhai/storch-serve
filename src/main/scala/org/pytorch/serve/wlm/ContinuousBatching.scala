package org.pytorch.serve.wlm

import java.util
import java.util.concurrent.ExecutionException
import org.pytorch.serve.job.Job
import org.pytorch.serve.util.messages.BaseModelRequest
import org.pytorch.serve.util.messages.ModelInferenceRequest
import org.pytorch.serve.util.messages.ModelLoadModelRequest
import org.pytorch.serve.util.messages.ModelWorkerResponse
import org.pytorch.serve.util.messages.Predictions
import org.pytorch.serve.util.messages.RequestInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
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
   *         stream response (not include the last stream) is sent
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
        else ContinuousBatching.logger.warn("Drop response for inference request {} due to client timeout", job.getPayload.getRequestId)
        val streamNext = prediction.getHeaders.get(org.pytorch.serve.util.messages.RequestInput.TS_STREAM_NEXT)
        if (streamNext == null || (streamNext != null && streamNext == "false")) jobs.remove(jobId)
        else if (!job.isOpen) {
          jobs.remove(job.getJobId)
          ContinuousBatching.logger.info("Connection to client got closed; Removing job: {}", job.getPayload.getRequestId)
        }
        else job.getPayload.setCachedInBackend(true)
      }
    }
    else {
//      import scala.collection.JavaConversions._
      for (j <- jobs.entrySet.asScala) {
        if (j.getValue == null) throw new IllegalStateException("Unexpected job in sendResponse() with non 200 status code: " + j.getKey)
        val job = j.getValue
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
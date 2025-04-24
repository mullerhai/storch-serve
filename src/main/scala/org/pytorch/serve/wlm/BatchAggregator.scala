package org.pytorch.serve.wlm

import org.pytorch.serve.job.Job
import org.pytorch.serve.util.messages.*
import org.pytorch.serve.wlm.WorkerState.*
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.concurrent.ExecutionException
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, TreeMap, Map as MutableMap}
import scala.jdk.CollectionConverters.*
object BatchAggregator {
  private val logger = LoggerFactory.getLogger(classOf[BatchAggregator])
}

class BatchAggregator {
  protected var model: Model = null
  protected var jobs: MutableMap[String, Job] = MutableMap.empty[String, Job]

  def this(model: Model) ={
    this()
    this.model = model
    jobs = new mutable.LinkedHashMap[String, Job]
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  def getRequest(threadName: String, state: WorkerState): BaseModelRequest = {
    cleanJobs()
    val req = new ModelInferenceRequest(model.getModelName)
    pollBatch(threadName, state)
    if (model.isUseJobTicket && jobs.isEmpty) {
      model.decNumJobTickets
      return req
    }
//    import scala.collection.JavaConversions._
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
  def sendResponse(message: ModelWorkerResponse): Boolean = {
    var jobDone = true
    // TODO: Handle prediction level code
    if (message.getCode == 200) {
      if (jobs.isEmpty) {
        // this is from initial load.
        BatchAggregator.logger.info("Jobs is empty. This is from initial load....")
        return true
      }

      for (prediction <- message.getPredictions) {
        val jobId = prediction.getRequestId
        val job = jobs.get(jobId).get
        BatchAggregator.logger.info("Sending response for jobId {}", jobId)
        if (job == null) throw new IllegalStateException("Unexpected job in sendResponse() with 200 status code: " + jobId)
        if (jobDone) {
          val streamNext = prediction.getHeaders.get(org.pytorch.serve.util.messages.RequestInput.TS_STREAM_NEXT)
          if (streamNext.isDefined && "true" == streamNext.get) jobDone = false
        }
        if (job.getPayload.getClientExpireTS > System.currentTimeMillis) job.response(prediction.getResp, prediction.getContentType, prediction.getStatusCode, prediction.getReasonPhrase, prediction.getHeaders)
        else BatchAggregator.logger.warn("Drop response for inference request {} due to client timeout", job.getPayload.getRequestId)
      }
    }
    else {
//      import scala.collection.JavaConversions._
      for (j <- jobs.toSeq) {
        if (j._2 == null) throw new IllegalStateException("Unexpected job in sendResponse() with non 200 status code: " + j._1)
        val job = j._2
        if (job.getPayload.getClientExpireTS > System.currentTimeMillis) job.sendError(message.getCode, message.getMessage)
        else BatchAggregator.logger.warn("Drop error response for inference request {} due to client timeout", job.getPayload.getRequestId)
      }
    }
    if (jobDone) cleanJobs()
    jobDone
  }

  def sendError(message: BaseModelRequest, error: String, status: Int): Unit = {
    if (message.isInstanceOf[ModelLoadModelRequest]) {
      BatchAggregator.logger.warn("Load model failed: {}, error: {}", message.getModelName, error)
      return
    }
    if (message != null) {
      val msg = message.asInstanceOf[ModelInferenceRequest]

      for (req <- msg.getRequestBatch) {
        val requestId = req.getRequestId
        val job = jobs.remove(requestId).get
        if (job == null) BatchAggregator.logger.error("Unexpected job in sendError(): " + requestId)
        else job.sendError(status, error)
      }
      if (!jobs.isEmpty) {
        cleanJobs()
        BatchAggregator.logger.error("Not all jobs got an error response.")
      }
    }
    else {
      // Send the error message to all the jobs
//      import scala.collection.JavaConversions._
      for (j <- jobs.toSeq) {
        val jobsId = j._2.getJobId
        val job = jobs.get(jobsId).get
        if (job.isControlCmd) job.sendError(status, error)
        else {
          // Data message can be handled by other workers.
          // If batch has gone past its batch max delay timer?
          handleErrorJob(job)
        }
      }
    }
    cleanJobs()
  }

  def cleanJobs(): Unit = {
    if (jobs != null) jobs.clear()
  }

  def handleErrorJob(job: Job): Unit = {
    model.addFirst(job)
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  def pollBatch(threadName: String, state: WorkerState): Unit = {
    model.pollBatch(threadName, if (state eq WorkerState.WORKER_MODEL_LOADED) 0
    else Long.MaxValue, jobs)
  }

  def shutdown(): Unit = {
  }

  def startEventDispatcher(): Unit = {
  }
}
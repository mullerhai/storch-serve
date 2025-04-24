package org.pytorch.serve.wlm

import org.pytorch.serve.job.Job
import org.pytorch.serve.util.messages.*
import org.pytorch.serve.wlm.WorkerState.*
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.concurrent.ExecutionException
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
object AsyncBatchAggregator {
  private val logger = LoggerFactory.getLogger(classOf[AsyncBatchAggregator])
}

class AsyncBatchAggregator(model: Model) extends BatchAggregator(model) {
  protected var jobs_in_backend = new mutable.LinkedHashMap[String, Job]

//  def this(model: Model)= {
//    this(model)
//    jobs_in_backend = new util.LinkedHashMap[String, Job]
//  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  override def getRequest(threadName: String, state: WorkerState): BaseModelRequest = {
    AsyncBatchAggregator.logger.info("Getting requests from model: {}", model)
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
        if ((j.getCmd eq WorkerCommands.STREAMPREDICT) || (j.getCmd eq WorkerCommands.STREAMPREDICT2)) req.setCommand(j.getCmd)
        req.addRequest(j.getPayload)
        jobs_in_backend.put(j.getJobId, j)
        jobs.remove(j.getJobId)
      }
    }
    req
  }

  @throws[InterruptedException]
  @throws[ExecutionException]
  override def pollBatch(threadName: String, state: WorkerState): Unit = {
    val newJobs = new mutable.LinkedHashMap[String, Job]
    model.pollBatch(threadName, if (state eq WorkerState.WORKER_MODEL_LOADED) 0
    else Long.MaxValue, newJobs)
    //    import scala.collection.JavaConversions._
    for (job <- newJobs.values) {
      jobs.put(job.getJobId, job)
      AsyncBatchAggregator.logger.debug("Adding job to jobs: {}", job.getJobId)
    }
  }

  /**
   * @param message : a response of a batch inference requests
   * @return - true: either a non-stream response or last stream response is sent - false: a
   *         stream response (not include the last stream) is sent
   */
  override def sendResponse(message: ModelWorkerResponse): Boolean = {
    val jobDone = true
    // TODO: Handle prediction level code
    if (message.getCode == 200) {
      if (message.getPredictions.isEmpty) {
        // this is from initial load.
        AsyncBatchAggregator.logger.info("Predictions is empty. This is from initial load....")
        jobs.clear()
        // jobs_in_backend.clear();
        return true
      }

      for (prediction <- message.getPredictions) {
        val jobId = prediction.getRequestId
        val job = jobs_in_backend.get(jobId)
        if (job.isEmpty) throw new IllegalStateException("Unexpected job in sendResponse() with 200 status code: " + jobId)
        if (job.get.getPayload.getClientExpireTS > System.currentTimeMillis) job.get.response(prediction.getResp, prediction.getContentType, prediction.getStatusCode, prediction.getReasonPhrase, prediction.getHeaders)
        else AsyncBatchAggregator.logger.warn("Drop response for inference request {} due to client timeout", job.get.getPayload.getRequestId)
        val streamNext = prediction.getHeaders.get(org.pytorch.serve.util.messages.RequestInput.TS_STREAM_NEXT)
        if ("false" == streamNext.get) jobs_in_backend.remove(jobId)
      }
    }
    else {

      for (j <- jobs_in_backend.toSeq) {
        if (j._2 == null) throw new IllegalStateException("Unexpected job in sendResponse() with non 200 status code: " + j._1)
        val job = j._2
        if (job.getPayload.getClientExpireTS > System.currentTimeMillis) job.sendError(message.getCode, message.getMessage)
        else AsyncBatchAggregator.logger.warn("Drop error response for inference request {} due to client timeout", job.getPayload.getRequestId)
      }
    }
    false
  }

  override def sendError(message: BaseModelRequest, error: String, status: Int): Unit = {
    if (message.isInstanceOf[ModelLoadModelRequest]) {
      AsyncBatchAggregator.logger.warn("Load model failed: {}, error: {}", message.getModelName, error)
      return
    }
    if (message != null) {
      val msg = message.asInstanceOf[ModelInferenceRequest]
//      import scala.collection.JavaConversions._
      for (req <- msg.getRequestBatch) {
        val requestId = req.getRequestId
        val job = jobs_in_backend.remove(requestId)
        if (job.isEmpty) AsyncBatchAggregator.logger.error("Unexpected job in sendError(): " + requestId)
        else job.get.sendError(status, error)
      }
      if (!jobs_in_backend.isEmpty) {
        // cleanJobs();
        AsyncBatchAggregator.logger.error("Not all jobs got an error response.")
      }
    }
    else {
      // Send the error message to all the jobs
      val entries = new ListBuffer[Map[String, Job]]() //jobs_in_backend.entrySet)
      entries += (jobs_in_backend.toMap)

      for (j <- entries.flatMap(_.toSeq)) {

        val jobsId = j._2.getJobId
        val job = jobs_in_backend.remove(jobsId)
        if (job.get.isControlCmd) job.get.sendError(status, error)
        else {
          // Data message can be handled by other workers.
          // If batch has gone past its batch max delay timer?
          handleErrorJob(job.get)
        }
      }
    }
  }

  override def handleErrorJob(job: Job): Unit = {
    model.addFirst(job)
  }
}
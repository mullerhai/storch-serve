package org.pytorch.serve.ensemble

import com.google.common.util.concurrent.ThreadFactoryBuilder

import java.util
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.archive.model.ModelVersionNotFoundException
import org.pytorch.serve.http.InternalServerException
import org.pytorch.serve.job.RestJob
import org.pytorch.serve.util.ApiUtils
import org.pytorch.serve.util.messages.InputParameter
import org.pytorch.serve.util.messages.RequestInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

object DagExecutor {
  private val logger = LoggerFactory.getLogger(classOf[DagExecutor])
}

class DagExecutor(private var dag: Dag) {

  private var inputRequestMap: util.Map[String, RequestInput] = new ConcurrentHashMap[String, RequestInput]

  def execute(input: RequestInput, topoSortedList: util.ArrayList[String]): util.ArrayList[NodeOutput] = {
    var executorCompletionService: CompletionService[NodeOutput] = null
    var executorService: ExecutorService = null
    if (topoSortedList == null) {
      val namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("wf-execute-thread-%d").build
      executorService = Executors.newFixedThreadPool(4, namedThreadFactory)
      executorCompletionService = new ExecutorCompletionService[NodeOutput](executorService)
    }
    val inDegreeMap = this.dag.getInDegreeMap
    val zeroInDegree = dag.getStartNodeNames
    val executing = new util.HashSet[String]
    if (topoSortedList == null) {
//      import scala.collection.JavaConversions._
      for (s <- zeroInDegree.asScala) {
        val newInput = new RequestInput(UUID.randomUUID.toString)
        newInput.setHeaders(input.getHeaders)
        newInput.setParameters(input.getParameters)
        inputRequestMap.put(s, newInput)
      }
    }
    val leafOutputs = new util.ArrayList[NodeOutput]
    while (!zeroInDegree.isEmpty) {
      val readyToExecute = new util.HashSet[String](zeroInDegree)
      readyToExecute.removeAll(executing)
      executing.addAll(readyToExecute)
      val outputs = new util.ArrayList[NodeOutput]
      if (topoSortedList == null) {
//        import scala.collection.JavaConversions._
        for (name <- readyToExecute.asScala) {
          executorCompletionService.submit(() => invokeModel(name, this.dag.getNodes.get(name).getWorkflowModel, inputRequestMap.get(name), 0))
        }
        try {
          val op = executorCompletionService.take
          if (op == null) throw new ExecutionException(new RuntimeException("WorkflowNode result empty"))
          else outputs.add(op.get)
        } catch {
          case e@(_: InterruptedException | _: ExecutionException) =>
            DagExecutor.logger.error(e.getMessage)
            val error = e.getMessage.split(":")
            throw new InternalServerException(error(error.length - 1)) // NOPMD
        }
      }
      else {
//        import scala.collection.JavaConversions._
        for (name <- readyToExecute.asScala) {
          outputs.add(new NodeOutput(name, null))
        }
      }
//      import scala.collection.JavaConversions._
      for (output <- outputs.asScala) {
        val nodeName = output.getNodeName
        executing.remove(nodeName)
        zeroInDegree.remove(nodeName)
        if (topoSortedList != null) topoSortedList.add(nodeName)
        val childNodes = this.dag.getDagMap.get(nodeName).get("outDegree")
        if (childNodes.isEmpty) leafOutputs.add(output)
        else {
//          import scala.collection.JavaConversions._
          for (newNodeName <- childNodes.asScala) {
            if (topoSortedList == null) {
              val response = output.getData.asInstanceOf[Array[Byte]]
              var newInput = this.inputRequestMap.get(newNodeName)
              if (newInput == null) {
                val params = new util.ArrayList[InputParameter]
                newInput = new RequestInput(UUID.randomUUID.toString)
                if (inDegreeMap.get(newNodeName) eq 1) params.add(new InputParameter("body", response))
                else params.add(new InputParameter(nodeName, response))
                newInput.setParameters(params)
                newInput.setHeaders(input.getHeaders)
              }
              else newInput.addParameter(new InputParameter(nodeName, response))
              this.inputRequestMap.put(newNodeName, newInput)
            }
            inDegreeMap.replace(newNodeName, inDegreeMap.get(newNodeName) - 1)
            if (inDegreeMap.get(newNodeName) eq 0) zeroInDegree.add(newNodeName)
          }
        }
      }
    }
    if (executorService != null) executorService.shutdown()
    leafOutputs
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  @throws[ExecutionException]
  @throws[InterruptedException]
  @tailrec
  private def invokeModel(nodeName: String, workflowModel: WorkflowModel, input: RequestInput, retryAttemptz: Int): NodeOutput = try {
    
    DagExecutor.logger.info(String.format("Invoking -  %s for attempt %d", nodeName, retryAttemptz))
    val respFuture = new CompletableFuture[Array[Byte]]
    val job = ApiUtils.addRESTInferenceJob(null, workflowModel.name, null, input)
    job.setResponsePromise(respFuture)
    val resp = respFuture.get(workflowModel.timeOutMs, TimeUnit.MILLISECONDS)
    new NodeOutput(nodeName, resp)
  } catch {
    case e@(_: InterruptedException | _: ExecutionException | _: TimeoutException) =>
      DagExecutor.logger.error(e.getMessage)
      var retryAttempt:Int = retryAttemptz
      if (retryAttempt < workflowModel.retryAttempts) {
        DagExecutor.logger.error(String.format("Timed out while executing %s for attempt %d", nodeName, retryAttempt))
        invokeModel(nodeName, workflowModel, input, {
          retryAttempt += 1; retryAttempt
        })
      }
      else {
        DagExecutor.logger.error(nodeName + " : " + e.getMessage)
        throw new InternalServerException(String.format("Failed to execute workflow Node after %d attempts : Error executing %s", retryAttempt, nodeName)) // NOPMD
      }
    case e: ModelNotFoundException =>
      DagExecutor.logger.error("Model not found.")
      DagExecutor.logger.error(e.getMessage)
      throw e
    case e: ModelVersionNotFoundException =>
      DagExecutor.logger.error("Model version not found.")
      DagExecutor.logger.error(e.getMessage)
      throw e
  }
}
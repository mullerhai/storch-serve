package org.pytorch.serve.ensemble

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.pytorch.serve.archive.model.{ModelNotFoundException, ModelVersionNotFoundException}
import org.pytorch.serve.http.InternalServerException
import org.pytorch.serve.job.RestJob
import org.pytorch.serve.util.ApiUtils
import org.pytorch.serve.util.messages.{InputParameter, RequestInput}
import org.slf4j.{Logger, LoggerFactory}

import java.util
import java.util.UUID
import java.util.concurrent.*
import scala.annotation.tailrec
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

object DagExecutor {
  private val logger = LoggerFactory.getLogger(classOf[DagExecutor])
}

class DagExecutor(private var dag: Dag) {

  //  private var inputRequestMap: mutable.Map[String, RequestInput] = new ConcurrentHashMap[String, RequestInput]
  private var inputRequestMap = new TrieMap[String, RequestInput]()

  def execute(input: RequestInput, topoSortedList: ListBuffer[String]): List[NodeOutput] = {
    var executorCompletionService: CompletionService[NodeOutput] = null
    var executorService: ExecutorService = null
    if (topoSortedList == null) {
      val namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("wf-execute-thread-%d").build
      executorService = Executors.newFixedThreadPool(4, namedThreadFactory)
      executorCompletionService = new ExecutorCompletionService[NodeOutput](executorService)
    }
    val inDegreeMap = this.dag.getInDegreeMap
    val zeroInDegree = dag.getStartNodeNames
    val executing = new mutable.HashSet[String]
    if (topoSortedList == null) {
//      import scala.collection.JavaConversions._
      for (s <- zeroInDegree) {
        val newInput = new RequestInput(UUID.randomUUID.toString)
        newInput.setHeaders(input.getHeaders)
        newInput.setParameters(input.getParameters)
        inputRequestMap.put(s, newInput)
      }
    }
    val leafOutputs = new ListBuffer[NodeOutput]
    while (!zeroInDegree.isEmpty) {
      val readyToExecute = new mutable.HashSet[String] //(zeroInDegree)
      readyToExecute.++=(zeroInDegree)
      readyToExecute.--=(executing)
      executing.++=(readyToExecute)
      val outputs = new ListBuffer[NodeOutput]
      if (topoSortedList == null) {
        for (name <- readyToExecute) {
          executorCompletionService.submit(() => invokeModel(name, this.dag.getNodes.get(name).get.getWorkflowModel, inputRequestMap.get(name).get, 0))
        }
        try {
          val op = executorCompletionService.take
          if (op == null) throw new ExecutionException(new RuntimeException("WorkflowNode result empty"))
          else outputs.append(op.get)
        } catch {
          case e@(_: InterruptedException | _: ExecutionException) =>
            DagExecutor.logger.error(e.getMessage)
            val error = e.getMessage.split(":")
            throw new InternalServerException(error(error.length - 1)) // NOPMD
        }
      }
      else {
        for (name <- readyToExecute) {
          outputs.append(new NodeOutput(name, null))
        }
      }

      for (output <- outputs) {
        val nodeName = output.getNodeName
        executing.remove(nodeName)
        zeroInDegree.remove(nodeName)
        if (topoSortedList != null) topoSortedList.append(nodeName)
        val childNodes = this.dag.getDagMap.get(nodeName).get("outDegree")
        if (childNodes.isEmpty) leafOutputs.append(output)
        else {
          for (newNodeName <- childNodes) {
            if (topoSortedList == null) {
              val response = output.getData.asInstanceOf[Array[Byte]]
              var newInput = this.inputRequestMap.get(newNodeName)
              if (newInput.isEmpty) {
                val params = new ListBuffer[InputParameter]
                val newInput = new RequestInput(UUID.randomUUID.toString)
                if (inDegreeMap.get(newNodeName) eq 1) params.append(new InputParameter("body", response))
                else params.append(new InputParameter(nodeName, response))
                newInput.setParameters(params.toList)
                newInput.setHeaders(input.getHeaders)
              }
              else newInput.get.addParameter(new InputParameter(nodeName, response))
              this.inputRequestMap.put(newNodeName, newInput.get)
            }
            inDegreeMap.update(newNodeName, inDegreeMap.get(newNodeName).get - 1)
            if (inDegreeMap.get(newNodeName) eq 0) zeroInDegree.add(newNodeName)
          }
        }
      }
    }
    if (executorService != null) executorService.shutdown()
    leafOutputs.toList
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
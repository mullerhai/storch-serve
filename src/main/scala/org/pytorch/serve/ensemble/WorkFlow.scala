package org.pytorch.serve.ensemble

import org.pytorch.serve.archive.workflow.{InvalidWorkflowException, WorkflowArchive}
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException

import java.io.{File, IOException, InputStreamReader, Reader}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
case class WorkflowModel(name: String, url: String, minWorkers: Int, maxWorkers: Int,  batchSize: Int, maxBatchDelay: Int, retryAttempts: Int, timeOutMs: Int, handler: String) 

object WorkFlow {
  @throws[IOException]
  @throws[InvalidWorkflowException]
  def readSpecFile(file: File) = {
    val yaml = new Yaml
    try {
      val r = new InputStreamReader(Files.newInputStream(file.toPath), StandardCharsets.UTF_8)
      try {
        @SuppressWarnings(Array("unchecked")) val loadedYaml = yaml.load(r).asInstanceOf[Map[String, AnyRef]]
        loadedYaml
      } catch {
        case e: YAMLException =>
          throw new InvalidWorkflowException("Failed to parse yaml.", e)
      } finally if (r != null) r.close()
    }
  }
}

@throws[IOException]
@throws[InvalidDAGException]
@throws[InvalidWorkflowException]
class WorkFlow(var workflowArchive: WorkflowArchive) {
  val models = new mutable.HashMap[String, WorkflowModel]
  var minWorkers = 1
  var maxWorkers = 1
  private var batchSize = 1
  private var maxBatchDelay = 50
  private var timeOutMs = 10000
  private var retryAttempts = 1
  private val dag = new Dag
  val specFile = new File(this.workflowArchive.getWorkflowDir, this.workflowArchive.getManifest.getWorkflow.getSpecFile)
  val handlerFile = new File(this.workflowArchive.getWorkflowDir, this.workflowArchive.getManifest.getWorkflow.getHandler)
  val workFlowName: String = this.workflowArchive.getWorkflowName
  @SuppressWarnings(Array("unchecked"))
  val spec: mutable.LinkedHashMap[String, AnyRef] = WorkFlow.readSpecFile(specFile).asInstanceOf[mutable.LinkedHashMap[String, AnyRef]]
  @SuppressWarnings(Array("unchecked")) val modelsInfo: Map[String, AnyRef] = this.workflowSpec.get("models").asInstanceOf[Map[String, AnyRef]]
  this.workflowSpec = spec.toMap
  @SuppressWarnings(Array("unchecked"))
  val dagInfo: Map[String, AnyRef] = this.workflowSpec.get("dag").asInstanceOf[Map[String, AnyRef]]

  for (entry <- modelsInfo.toSeq) {
    val keyName = entry._1
    keyName match {
      case "min-workers" =>
        minWorkers = entry._2.asInstanceOf[Int]
      case "max-workers" =>
        maxWorkers = entry._2.asInstanceOf[Int]
      case "batch-size" =>
        batchSize = entry._2.asInstanceOf[Int]
      case "max-batch-delay" =>
        maxBatchDelay = entry._2.asInstanceOf[Int]
      case "retry-attempts" =>
        retryAttempts = entry._2.asInstanceOf[Int]
      case "timeout-ms" =>
        timeOutMs = entry._2.asInstanceOf[Int]
      case _ =>
        // entry.getValue().getClass() check object type.
        // assuming Map containing model info
        @SuppressWarnings(Array("unchecked"))
        val model = entry._2.asInstanceOf[mutable.LinkedHashMap[String, AnyRef]]
        val modelName = workFlowName + "__" + keyName
        val wfm = WorkflowModel(modelName, model.get("url").asInstanceOf[String], model.getOrElse("min-workers", minWorkers.toString).asInstanceOf[Int], model.getOrElse("max-workers", maxWorkers.toString).asInstanceOf[Int], model.getOrElse("batch-size", batchSize.toString).asInstanceOf[Int], model.getOrElse("max-batch-delay", maxBatchDelay.toString).asInstanceOf[Int], model.getOrElse("retry-attempts", retryAttempts.toString).asInstanceOf[Int], model.getOrElse("timeout-ms", timeOutMs.toString).asInstanceOf[Int], null)
        models.put(modelName, wfm)
    }
  }
  private var workflowSpec: Map[String, AnyRef] = null

//  import scala.collection.JavaConversions._

  for (entry <- dagInfo.toSeq) {
    val nodeName = entry._1
    val modelName = workFlowName + "__" + nodeName
    var wfm: WorkflowModel = null
    if (!models.contains(modelName)) wfm = new WorkflowModel(modelName, null, 1, 1, 1, 0, retryAttempts, timeOutMs, handlerFile.getPath + ":" + nodeName)
    else wfm = models.get(modelName).get
    val fromNode = new Node(nodeName, wfm)
    dag.addNode(fromNode)
    @SuppressWarnings(Array("unchecked"))
    val values = entry._2.asInstanceOf[List[String]]
//    import scala.collection.JavaConversions._
    import scala.util.control.Breaks.{break, breakable}
    for (toNodeName <- values) {
      breakable(
        if (toNodeName == null || "" == toNodeName.trim)
         break() // continue //todo: continue is not supported
      )
 
      val toModelName = workFlowName + "__" + toNodeName
      var toWfm: WorkflowModel = null
      if (!models.contains(toModelName)) toWfm = new WorkflowModel(toModelName, null, 1, 1, 1, 0, retryAttempts, timeOutMs, handlerFile.getPath + ":" + toNodeName)
      else toWfm = models.get(toModelName).get
      val toNode = new Node(toNodeName, toWfm)
      dag.addNode(toNode)
      dag.addEdge(fromNode, toNode)
    }
  }
  dag.validate


  def getWorkflowSpec: AnyRef = workflowSpec

  def getDag: Dag = this.dag

  def getWorkflowArchive: WorkflowArchive = workflowArchive

  def getMinWorkers: Int = minWorkers

  def setMinWorkers(minWorkers: Int): Unit = {
    this.minWorkers = minWorkers
  }

  def getMaxWorkers: Int = maxWorkers

  def setMaxWorkers(maxWorkers: Int): Unit = {
    this.maxWorkers = maxWorkers
  }

  def getBatchSize: Int = batchSize

  def setBatchSize(batchSize: Int): Unit = {
    this.batchSize = batchSize
  }

  def getMaxBatchDelay: Int = maxBatchDelay

  def setMaxBatchDelay(maxBatchDelay: Int): Unit = {
    this.maxBatchDelay = maxBatchDelay
  }

  def getWorkflowDag: String = this.workflowSpec.get("dag").toString
}
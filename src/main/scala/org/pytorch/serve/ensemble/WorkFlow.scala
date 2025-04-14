package org.pytorch.serve.ensemble

import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util
import org.pytorch.serve.archive.workflow.InvalidWorkflowException
import org.pytorch.serve.archive.workflow.WorkflowArchive
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import scala.jdk.CollectionConverters._
case class WorkflowModel(name: String, url: String, minWorkers: Int, maxWorkers: Int,  batchSize: Int, maxBatchDelay: Int, retryAttempts: Int, timeOutMs: Int, handler: String) 

object WorkFlow {
  @throws[IOException]
  @throws[InvalidWorkflowException]
  def readSpecFile(file: File) = {
    val yaml = new Yaml
    try {
      val r = new InputStreamReader(Files.newInputStream(file.toPath), StandardCharsets.UTF_8)
      try {
        @SuppressWarnings(Array("unchecked")) val loadedYaml = yaml.load(r).asInstanceOf[util.Map[String, AnyRef]]
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
  private var workflowSpec: util.Map[String, AnyRef] = null
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
  val models = new util.HashMap[String, WorkflowModel]
  @SuppressWarnings(Array("unchecked")) 
  val spec: util.LinkedHashMap[String, AnyRef] = WorkFlow.readSpecFile(specFile).asInstanceOf[util.LinkedHashMap[String, AnyRef]]
  this.workflowSpec = spec
  @SuppressWarnings(Array("unchecked")) val modelsInfo: util.Map[String, AnyRef] = this.workflowSpec.get("models").asInstanceOf[util.Map[String, AnyRef]]

//  import scala.collection.JavaConversions._

  for (entry <- modelsInfo.entrySet.asScala) {
    val keyName = entry.getKey
    keyName match {
      case "min-workers" =>
        minWorkers = entry.getValue.asInstanceOf[Int]
      case "max-workers" =>
        maxWorkers = entry.getValue.asInstanceOf[Int]
      case "batch-size" =>
        batchSize = entry.getValue.asInstanceOf[Int]
      case "max-batch-delay" =>
        maxBatchDelay = entry.getValue.asInstanceOf[Int]
      case "retry-attempts" =>
        retryAttempts = entry.getValue.asInstanceOf[Int]
      case "timeout-ms" =>
        timeOutMs = entry.getValue.asInstanceOf[Int]
      case _ =>
        // entry.getValue().getClass() check object type.
        // assuming Map containing model info
        @SuppressWarnings(Array("unchecked")) 
        val model = entry.getValue.asInstanceOf[util.LinkedHashMap[String, AnyRef]]
        val modelName = workFlowName + "__" + keyName
        val wfm = WorkflowModel(modelName, model.get("url").asInstanceOf[String], model.getOrDefault("min-workers", minWorkers.toString).asInstanceOf[Int], model.getOrDefault("max-workers", maxWorkers.toString).asInstanceOf[Int], model.getOrDefault("batch-size", batchSize.toString).asInstanceOf[Int], model.getOrDefault("max-batch-delay", maxBatchDelay.toString).asInstanceOf[Int], model.getOrDefault("retry-attempts", retryAttempts.toString).asInstanceOf[Int], model.getOrDefault("timeout-ms", timeOutMs.toString).asInstanceOf[Int], null)
        models.put(modelName, wfm)
    }
  }
  @SuppressWarnings(Array("unchecked")) 
  val dagInfo: util.Map[String, AnyRef] = this.workflowSpec.get("dag").asInstanceOf[util.Map[String, AnyRef]]

//  import scala.collection.JavaConversions._

  for (entry <- dagInfo.entrySet.asScala) {
    val nodeName = entry.getKey
    val modelName = workFlowName + "__" + nodeName
    var wfm: WorkflowModel = null
    if (!models.containsKey(modelName)) wfm = new WorkflowModel(modelName, null, 1, 1, 1, 0, retryAttempts, timeOutMs, handlerFile.getPath + ":" + nodeName)
    else wfm = models.get(modelName)
    val fromNode = new Node(nodeName, wfm)
    dag.addNode(fromNode)
    @SuppressWarnings(Array("unchecked")) val values = entry.getValue.asInstanceOf[util.ArrayList[String]]
//    import scala.collection.JavaConversions._
    import scala.util.control.Breaks.{break, breakable}
    for (toNodeName <- values.asScala) {
      breakable(
        if (toNodeName == null || "" == toNodeName.strip)
         break() // continue //todo: continue is not supported
      )
 
      val toModelName = workFlowName + "__" + toNodeName
      var toWfm: WorkflowModel = null
      if (!models.containsKey(toModelName)) toWfm = new WorkflowModel(toModelName, null, 1, 1, 1, 0, retryAttempts, timeOutMs, handlerFile.getPath + ":" + toNodeName)
      else toWfm = models.get(toModelName)
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
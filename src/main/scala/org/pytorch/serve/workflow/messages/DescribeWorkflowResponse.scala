package org.pytorch.serve.workflow.messages

import scala.jdk.CollectionConverters._

case class DescribeWorkflowResponse (
   var workflowName: String = null,
   var workflowUrl: String = null,
   var minWorkers: Int = 0,
   var maxWorkers: Int = 0,
   var batchSize: Int = 0,
   var maxBatchDelay: Int = 0,
   var workflowDag: String = null)
//class DescribeWorkflowResponse {
//  private var workflowName: String = null
//  private var workflowUrl: String = null
//  private var minWorkers = 0
//  private var maxWorkers = 0
//  private var batchSize = 0
//  private var maxBatchDelay = 0
//  private var workflowDag: String = null
//
//  def getWorkflowName: String = workflowName
//
//  def setWorkflowName(workflowName: String): Unit = {
//    this.workflowName = workflowName
//  }
//
//  def getWorkflowUrl: String = workflowUrl
//
//  def setWorkflowUrl(workflowUrl: String): Unit = {
//    this.workflowUrl = workflowUrl
//  }
//
//  def getMinWorkers: Int = minWorkers
//
//  def setMinWorkers(minWorkers: Int): Unit = {
//    this.minWorkers = minWorkers
//  }
//
//  def getMaxWorkers: Int = maxWorkers
//
//  def setMaxWorkers(maxWorkers: Int): Unit = {
//    this.maxWorkers = maxWorkers
//  }
//
//  def getBatchSize: Int = batchSize
//
//  def setBatchSize(batchSize: Int): Unit = {
//    this.batchSize = batchSize
//  }
//
//  def getMaxBatchDelay: Int = maxBatchDelay
//
//  def setMaxBatchDelay(maxBatchDelay: Int): Unit = {
//    this.maxBatchDelay = maxBatchDelay
//  }
//
//  def getWorkflowDag: String = workflowDag
//
//  def setWorkflowDag(workflowDag: String): Unit = {
//    this.workflowDag = workflowDag
//  }
//}
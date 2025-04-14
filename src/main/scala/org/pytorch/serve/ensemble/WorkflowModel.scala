//package org.pytorch.serve.ensemble
//
//class WorkflowModel(private var name: String, private var url: String, private var minWorkers: Int, private var maxWorkers: Int, private var batchSize: Int, private var maxBatchDelay: Int, private var retryAttempts: Int, private var timeOutMs: Int, private var handler: String) {
//  def getName: String = name
//
//  def setName(name: String): Unit = {
//    this.name = name
//  }
//
//  def getUrl: String = url
//
//  def setUrl(url: String): Unit = {
//    this.url = url
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
//  def getRetryAttempts: Int = retryAttempts
//
//  def setRetryAttempts(retryAttempts: Int): Unit = {
//    this.retryAttempts = retryAttempts
//  }
//
//  def getTimeOutMs: Int = timeOutMs
//
//  def setTimeOutMs(timeOutMs: Int): Unit = {
//    this.timeOutMs = timeOutMs
//  }
//
//  def getHandler: String = handler
//}
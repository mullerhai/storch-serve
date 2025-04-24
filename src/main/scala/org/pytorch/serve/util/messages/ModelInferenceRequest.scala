package org.pytorch.serve.util.messages

import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
class ModelInferenceRequest(modelName: String) extends BaseModelRequest(WorkerCommands.PREDICT, modelName) {

  private var batch: ListBuffer[RequestInput] = new ListBuffer[RequestInput]

  def getRequestBatch: List[RequestInput] = batch.toList

  def setRequestBatch(requestBatch: List[RequestInput]): Unit = {
    this.batch.appendAll(requestBatch)
  }

  def addRequest(req: RequestInput): Unit = {
    batch.append(req)
  }

  def setCachedInBackend(cached: Boolean): Unit = {
//    import scala.collection.JavaConversions._
    for (input <- batch) {
      input.setCachedInBackend(cached)
    }
  }
}
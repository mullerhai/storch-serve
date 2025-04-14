package org.pytorch.serve.util.messages

import java.util
import scala.jdk.CollectionConverters._
class ModelInferenceRequest(modelName: String) extends BaseModelRequest(WorkerCommands.PREDICT, modelName) {
  
  private var batch: util.List[RequestInput] = new util.ArrayList[RequestInput]

  def getRequestBatch: util.List[RequestInput] = batch

  def setRequestBatch(requestBatch: util.List[RequestInput]): Unit = {
    this.batch = requestBatch
  }

  def addRequest(req: RequestInput): Unit = {
    batch.add(req)
  }

  def setCachedInBackend(cached: Boolean): Unit = {
//    import scala.collection.JavaConversions._
    for (input <- batch.asScala) {
      input.setCachedInBackend(cached)
    }
  }
}
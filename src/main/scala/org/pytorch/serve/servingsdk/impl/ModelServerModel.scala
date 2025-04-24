package org.pytorch.serve.servingsdk.impl

import org.pytorch.serve.servingsdk.{Worker, Model as Mo}
import org.pytorch.serve.wlm.{Model, WorkerThread}
import org.pytorch.serve.wlm.{ModelManager, Model as Mod}

import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
class ModelServerModel(val model: Mod) extends Mo {
  override def getModelName: String = model.getModelName

  override def getModelUrl: String = model.getModelUrl

  override def getModelHandler: String = model.getModelArchive.getHandler

  override def getModelWorkers: util.List[Worker] = {
    val list = new ListBuffer[Worker]
    ModelManager.getInstance.getWorkers(model.getModelVersionName).
      foreach((r: WorkerThread) => list.append(new ModelWorker(r)))
    list.toList.asJava
  }
}
package org.pytorch.serve.servingsdk.impl

import org.pytorch.serve.wlm.{Model, WorkerThread}

import java.util
import org.pytorch.serve.servingsdk.Worker
import org.pytorch.serve.wlm.ModelManager
import org.pytorch.serve.servingsdk.Model as Mo

import org.pytorch.serve.wlm.Model as Mod
class ModelServerModel(val model: Mod) extends Mo {
  override def getModelName: String = model.getModelName

  override def getModelUrl: String = model.getModelUrl

  override def getModelHandler: String = model.getModelArchive.getHandler

  override def getModelWorkers: util.List[Worker] = {
    val list = new util.ArrayList[Worker]
    ModelManager.getInstance.getWorkers(model.getModelVersionName).
      forEach((r: WorkerThread) => list.add(new ModelWorker(r)))
    list
  }
}
package org.pytorch.serve.servingsdk.impl

import org.pytorch.serve.servingsdk.Worker
import org.pytorch.serve.wlm.WorkerState
import org.pytorch.serve.wlm.WorkerThread

class ModelWorker(t: WorkerThread) extends Worker {
  private var running = t.getState eq WorkerState.WORKER_MODEL_LOADED
  private var memory = t.getMemory


  override def isRunning: Boolean = running

  override def getWorkerMemory: Long = memory
}
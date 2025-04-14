package org.pytorch.serve.wlm

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class WorkerStateListener(var future: CompletableFuture[Integer], count: Int) {
  
  private var counts: AtomicInteger = new AtomicInteger(count)

  def notifyChangeState(modelName: String, state: WorkerState, status: Integer): Unit = {
    // Update success and fail counts
    if (state eq WorkerState.WORKER_MODEL_LOADED) if (counts.decrementAndGet == 0) future.complete(status)
    if ((state eq WorkerState.WORKER_ERROR) || (state eq WorkerState.WORKER_STOPPED)) future.complete(status)
  }
}
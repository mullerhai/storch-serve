package org.pytorch.serve.wlm

enum WorkerState:
  case WORKER_STARTED, WORKER_MODEL_LOADED, WORKER_STOPPED, WORKER_ERROR, WORKER_SCALED_DOWN
//object WorkerState extends Enumeration {
//  type WorkerState = Value
//  val WORKER_STARTED, WORKER_MODEL_LOADED, WORKER_STOPPED, WORKER_ERROR, WORKER_SCALED_DOWN = Value
//}
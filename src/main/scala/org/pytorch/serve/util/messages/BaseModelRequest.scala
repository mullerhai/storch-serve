package org.pytorch.serve.util.messages

class BaseModelRequest {
  private var command: WorkerCommands = null
  private var modelName: String = null

  def this(command: WorkerCommands, modelName: String)= {
    this()
    this.command = command
    this.modelName = modelName
  }

  def getCommand: WorkerCommands = command

  def setCommand(workerCommands: WorkerCommands): Unit = {
    this.command = workerCommands
  }

  def getModelName: String = modelName
}
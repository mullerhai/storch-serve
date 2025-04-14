package org.pytorch.serve.job

import java.util
import org.pytorch.serve.util.messages.RequestInput
import org.pytorch.serve.util.messages.WorkerCommands.{PREDICT, LOAD, UNLOAD, STATS, DESCRIBE, STREAMPREDICT, STREAMPREDICT2, OIPPREDICT}
import org.pytorch.serve.util.messages.WorkerCommands
abstract class Job(var modelName: String, var modelVersion: String, var cmd: WorkerCommands // Else its data msg or inf requests
                   , var input: RequestInput) {

  private var begin = System.nanoTime
  private var scheduled = begin

  def getJobId: String = input.getRequestId

  def getModelName: String = modelName

  def getModelVersion: String = modelVersion

  def getCmd: WorkerCommands = cmd

  def isControlCmd: Boolean = cmd match {
    case PREDICT => false
    case OIPPREDICT => false
    case STREAMPREDICT => false
    case STREAMPREDICT2 => false
    case DESCRIBE =>
      false
    case _ =>
      true
  }

  def getPayload: RequestInput = input

  def setScheduled(): Unit = {
    scheduled = System.nanoTime
  }

  def getBegin: Long = begin

  def getScheduled: Long = scheduled

  def response(body: Array[Byte], contentType: CharSequence, statusCode: Int, statusPhrase: String, responseHeaders: util.Map[String, String]): Unit

  def sendError(status: Int, error: String): Unit

  def getGroupId: String = {
    if (input != null) return input.getSequenceId
    null
  }

  def isOpen: Boolean
}
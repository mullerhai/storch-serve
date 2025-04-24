package org.pytorch.serve.util.messages

import java.util
import scala.collection.mutable.ListBuffer

class ModelWorkerResponse {
  private var code = 0
  private var message: String = null
  private var predictions: ListBuffer[Predictions] = new ListBuffer[Predictions]

  def getCode: Int = code

  def setCode(code: Int): Unit = {
    this.code = code
  }

  def getMessage: String = message

  def setMessage(message: String): Unit = {
    this.message = message
  }

  def getPredictions: List[Predictions] = predictions.toList

  def setPredictions(predictions: List[Predictions]): Unit = {
    this.predictions.appendAll(predictions)
  }

  def appendPredictions(prediction: Predictions): Unit = {
    this.predictions.append(prediction)
  }
}
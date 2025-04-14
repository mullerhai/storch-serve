package org.pytorch.serve.util.messages

import java.util

class ModelWorkerResponse {
  private var code = 0
  private var message: String = null
  private var predictions: util.List[Predictions] = null

  def getCode: Int = code

  def setCode(code: Int): Unit = {
    this.code = code
  }

  def getMessage: String = message

  def setMessage(message: String): Unit = {
    this.message = message
  }

  def getPredictions: util.List[Predictions] = predictions

  def setPredictions(predictions: util.List[Predictions]): Unit = {
    this.predictions = predictions
  }

  def appendPredictions(prediction: Predictions): Unit = {
    this.predictions.add(prediction)
  }
}
package org.pytorch.serve.http.messages

class KFV1ModelReadyResponse {
  private var name: String = null
  private var ready = false

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }

  def getReady: Boolean = ready

  def setReady(ready: Boolean): Unit = {
    this.ready = ready
  }
}
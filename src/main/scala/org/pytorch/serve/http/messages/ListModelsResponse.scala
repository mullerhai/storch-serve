package org.pytorch.serve.http.messages

import java.util

object ListModelsResponse {
  final class ModelItem {
    private var modelName: String = null
    private var modelUrl: String = null

    def this(modelName: String, modelUrl: String)= {
      this()
      this.modelName = modelName
      this.modelUrl = modelUrl
    }

    def getModelName: String = modelName

    def setModelName(modelName: String): Unit = {
      this.modelName = modelName
    }

    def getModelUrl: String = modelUrl

    def setModelUrl(modelUrl: String): Unit = {
      this.modelUrl = modelUrl
    }
  }
}

class ListModelsResponse {
  models = new util.ArrayList[ListModelsResponse.ModelItem]
  private var nextPageToken: String = null
  private var models: util.List[ListModelsResponse.ModelItem] = null

  def getNextPageToken: String = nextPageToken

  def setNextPageToken(nextPageToken: String): Unit = {
    this.nextPageToken = nextPageToken
  }

  def getModels: util.List[ListModelsResponse.ModelItem] = models

  def setModels(models: util.List[ListModelsResponse.ModelItem]): Unit = {
    this.models = models
  }

  def addModel(modelName: String, modelUrl: String): Unit = {
    models.add(new ListModelsResponse.ModelItem(modelName, modelUrl))
  }
}
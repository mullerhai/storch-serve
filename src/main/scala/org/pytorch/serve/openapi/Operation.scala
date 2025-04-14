package org.pytorch.serve.openapi

import java.util

class Operation {
  private var summary: String = null
  private var description: String = null
  private var operationId: String = null
  private var parameters = new util.ArrayList[Parameter]
  private var requestBody: RequestBody = null
  private var responses: util.Map[String, Response] = null
  private var deprecated = false

  def this(operationId: String, description: String) ={
    this()
    this.operationId = operationId
    this.description = description
  }

  def this(operationId: String) ={
    this(operationId, null)
  }

  def getSummary: String = summary

  def setSummary(summary: String): Unit = {
    this.summary = summary
  }

  def getDescription: String = description

  def setDescription(description: String): Unit = {
    this.description = description
  }

  def getOperationId: String = operationId

  def setOperationId(operationId: String): Unit = {
    this.operationId = operationId
  }

  def getParameters: util.List[Parameter] = parameters

  def setParameters(parameters: util.ArrayList[Parameter]): Unit = {
    this.parameters = parameters
  }

  def addParameter(parameter: Parameter): Unit = {
    if (parameters == null) parameters = new util.ArrayList[Parameter]
    parameters.add(parameter)
  }

  def getRequestBody: RequestBody = requestBody

  def setRequestBody(requestBody: RequestBody): Unit = {
    this.requestBody = requestBody
  }

  def getResponses: util.Map[String, Response] = responses

  def setResponses(responses: util.Map[String, Response]): Unit = {
    this.responses = responses
  }

  def addResponse(response: Response): Unit = {
    if (responses == null) responses = new util.LinkedHashMap[String, Response]
    responses.put(response.getCode, response)
  }

  def getDeprecated: Boolean = deprecated

  def setDeprecated(deprecated: Boolean): Unit = {
    this.deprecated = deprecated
  }
}
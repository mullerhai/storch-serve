package org.pytorch.serve.openapi

import java.util
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class Operation {
  private var summary: String = null
  private var description: String = null
  private var operationId: String = null
  private var parameters = new ListBuffer[Parameter]
  private var requestBody: RequestBody = null
  private var responses: mutable.Map[String, Response] = null
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

  def getParameters: List[Parameter] = parameters.toList

  def setParameters(parameters: List[Parameter]): Unit = {
    this.parameters.appendAll(parameters)
  }

  def addParameter(parameter: Parameter): Unit = {
    if (parameters == null) parameters = new ListBuffer[Parameter]
    parameters.append(parameter)
  }

  def getRequestBody: RequestBody = requestBody

  def setRequestBody(requestBody: RequestBody): Unit = {
    this.requestBody = requestBody
  }

  def getResponses: mutable.Map[String, Response] = responses

  def setResponses(responses: Map[String, Response]): Unit = {
    //    responses.toList.map((k,v) => this.responses.+=(k -> v))
    this.responses.++=(responses)
  }

  def addResponse(response: Response): Unit = {
    if (responses == null) responses = new mutable.LinkedHashMap[String, Response]
    responses.put(response.getCode, response)
  }

  def getDeprecated: Boolean = deprecated

  def setDeprecated(deprecated: Boolean): Unit = {
    this.deprecated = deprecated
  }
}
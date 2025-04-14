package org.pytorch.serve.workflow.messages

import org.pytorch.serve.http.StatusResponse
import scala.jdk.CollectionConverters._

case class ModelRegistrationResult(modelName: String,  response: StatusResponse)
//{
//  def getModelName: String = modelName
//
//  def getResponse: StatusResponse = response
//}
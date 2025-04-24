package org.pytorch.serve.openapi

import java.util
import scala.collection.mutable

class RequestBody {
  private var description: String = null
  private var content: mutable.Map[String, MediaType] = mutable.Map.empty
  private var required = false

  def getDescription: String = description

  def setDescription(description: String): Unit = {
    this.description = description
  }

  def getContent: Map[String, MediaType] = content.toMap

  def setContent(content: Map[String, MediaType]): Unit = {
    this.content ++= content
  }

  def addContent(mediaType: MediaType): Unit = {
    if (content == null) content = new mutable.LinkedHashMap[String, MediaType]
    content.put(mediaType.getContentType, mediaType)
  }

  def isRequired: Boolean = required

  def setRequired(required: Boolean): Unit = {
    this.required = required
  }
}
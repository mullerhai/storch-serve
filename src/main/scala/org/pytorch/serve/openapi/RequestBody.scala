package org.pytorch.serve.openapi

import java.util

class RequestBody {
  private var description: String = null
  private var content: util.Map[String, MediaType] = null
  private var required = false

  def getDescription: String = description

  def setDescription(description: String): Unit = {
    this.description = description
  }

  def getContent: util.Map[String, MediaType] = content

  def setContent(content: util.Map[String, MediaType]): Unit = {
    this.content = content
  }

  def addContent(mediaType: MediaType): Unit = {
    if (content == null) content = new util.LinkedHashMap[String, MediaType]
    content.put(mediaType.getContentType, mediaType)
  }

  def isRequired: Boolean = required

  def setRequired(required: Boolean): Unit = {
    this.required = required
  }
}
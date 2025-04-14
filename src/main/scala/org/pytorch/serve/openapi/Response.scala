package org.pytorch.serve.openapi

import java.util

class Response {
  @transient 
  private var code: String = null
  private var description: String = null
  private var content: util.Map[String, MediaType] = null

  def this(code: String, description: String) ={
    this()
    this.code = code
    this.description = description
  }

  def this(code: String, description: String, mediaType: MediaType)= {
    this()
    this.code = code
    this.description = description
    content = new util.LinkedHashMap[String, MediaType]
    content.put(mediaType.getContentType, mediaType)
  }

  def getCode: String = code

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
}
package org.pytorch.serve.openapi

import java.util
import scala.collection.mutable

class Response {
  @transient 
  private var code: String = null
  private var description: String = null
  private var content: mutable.Map[String, MediaType] = null

  def this(code: String, description: String) ={
    this()
    this.code = code
    this.description = description
  }

  def this(code: String, description: String, mediaType: MediaType)= {
    this()
    this.code = code
    this.description = description
    content = new mutable.LinkedHashMap[String, MediaType]
    content.put(mediaType.getContentType, mediaType)
  }

  def getCode: String = code

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
}
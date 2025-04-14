package org.pytorch.serve.openapi

import java.util

class MediaType {
  @transient private var contentType: String = null
  private var schema: Schema = null
  private var encoding: util.Map[String, Encoding] = null

  def this(contentType: String, schema: Schema) ={
    this()
    this.contentType = contentType
    this.schema = schema
  }

  def getContentType: String = contentType

  def setContentType(contentType: String): Unit = {
    this.contentType = contentType
  }

  def getSchema: Schema = schema

  def setSchema(schema: Schema): Unit = {
    this.schema = schema
  }

  def getEncoding: util.Map[String, Encoding] = encoding

  def setEncoding(encoding: util.Map[String, Encoding]): Unit = {
    this.encoding = encoding
  }

  def addEncoding(contentType: String, encoding: Encoding): Unit = {
    if (this.encoding == null) this.encoding = new util.LinkedHashMap[String, Encoding]
    this.encoding.put(contentType, encoding)
  }
}
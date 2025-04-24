package org.pytorch.serve.openapi

import java.util
import scala.collection.mutable

class MediaType {
  @transient private var contentType: String = null
  private var schema: Schema = null
  private var encoding: mutable.Map[String, Encoding] = mutable.Map.empty

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

  def getEncoding: Map[String, Encoding] = encoding.toMap

  def setEncoding(encoding: mutable.Map[String, Encoding]): Unit = {
    this.encoding = encoding
  }

  def addEncoding(contentType: String, encoding: Encoding): Unit = {
    if (this.encoding == null) this.encoding = new mutable.LinkedHashMap[String, Encoding]
    this.encoding.put(contentType, encoding)
  }
}
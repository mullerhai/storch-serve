package org.pytorch.serve.util.messages

import java.nio.charset.StandardCharsets

class InputParameter {
  private var name: String = null
  private var value: Array[Byte] = null
  private var contentType: CharSequence = null

  def this(name: String, value: String)= {
    this()
    this.name = name
    this.value = value.getBytes(StandardCharsets.UTF_8)
  }

  def this(name: String, data: Array[Byte], contentType: CharSequence) ={
    this()
    this.name = name
    this.contentType = contentType
    this.value = data.clone
  }

  def this(name: String, data: Array[Byte])= {
    this(name, data, null)
  }

  def getName: String = name

  def getValue: Array[Byte] = value

  def getContentType: CharSequence = contentType
}
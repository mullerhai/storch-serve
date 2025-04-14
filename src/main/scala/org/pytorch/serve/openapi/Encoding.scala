package org.pytorch.serve.openapi

class Encoding {
  private var contentType: String = null
  private var style: String = null
  private var explode = false
  private var allowReserved = false

  def this(contentType: String) ={
    this()
    this.contentType = contentType
  }

  def getContentType: String = contentType

  def setContentType(contentType: String): Unit = {
    this.contentType = contentType
  }

  def isAllowReserved: Boolean = allowReserved

  def setAllowReserved(allowReserved: Boolean): Unit = {
    this.allowReserved = allowReserved
  }

  def getStyle: String = style

  def setStyle(style: String): Unit = {
    this.style = style
  }

  def isExplode: Boolean = explode

  def setExplode(explode: Boolean): Unit = {
    this.explode = explode
  }
}
package org.pytorch.serve.http

class ErrorResponse {
  private var code = 0
  private var `type`: String = null
  private var message: String = null

  def this(code: Int, message: String)= {
    this()
    this.code = code
    this.message = message
  }

  def this(code: Int, `type`: String, message: String) ={
    this()
    this.code = code
    this.`type` = `type`
    this.message = message
  }

  def getCode: Int = code

  def getType: String = `type`

  def getMessage: String = message
}
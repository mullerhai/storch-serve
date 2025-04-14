package org.pytorch.serve.http

import com.google.gson.annotations.Expose

class StatusResponse {
  private var httpResponseCode = 0
  @Expose 
  private var status: String = null
  private var e: Throwable = null

  def this(status: String, httpResponseCode: Int)= {
    this()
    this.status = status
    this.httpResponseCode = httpResponseCode
  }

  def getHttpResponseCode: Int = httpResponseCode

  def setHttpResponseCode(httpResponseCode: Int): Unit = {
    this.httpResponseCode = httpResponseCode
  }

  def getStatus: String = status

  def setStatus(status: String): Unit = {
    this.status = status
  }

  def getE: Throwable = e

  def setE(e: Throwable): Unit = {
    this.e = e
  }
}
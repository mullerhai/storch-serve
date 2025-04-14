package org.pytorch.serve.openapi

class Info {
  private var title: String = null
  private var description: String = null
  private var termsOfService: String = null
  private var version: String = null

  def getTitle: String = title

  def setTitle(title: String): Unit = {
    this.title = title
  }

  def getDescription: String = description

  def setDescription(description: String): Unit = {
    this.description = description
  }

  def getTermsOfService: String = termsOfService

  def setTermsOfService(termsOfService: String): Unit = {
    this.termsOfService = termsOfService
  }

  def getVersion: String = version

  def setVersion(version: String): Unit = {
    this.version = version
  }
}
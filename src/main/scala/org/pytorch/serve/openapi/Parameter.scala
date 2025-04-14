package org.pytorch.serve.openapi

@SuppressWarnings(Array("PMD.AbstractClassWithoutAbstractMethod")) 
abstract class Parameter {
  protected var `type`: String = null
  protected var in: String = null
  protected var name: String = null
  protected var description: String = null
  protected var required = false
  protected var deprecated = false
  protected var allowEmptyValue = false
  protected var style: String = null
  protected var explode = false
  protected var schema: Schema = null

  def setType(`type`: String): Unit = {
    this.`type` = `type`
  }

  def getType: String = `type`

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }

  def getIn: String = in

  def setIn(in: String): Unit = {
    this.in = in
  }

  def getDescription: String = description

  def setDescription(description: String): Unit = {
    this.description = description
  }

  def isRequired: Boolean = required

  def setRequired(required: Boolean): Unit = {
    this.required = required
  }

  def getDeprecated: Boolean = deprecated

  def setDeprecated(deprecated: Boolean): Unit = {
    this.deprecated = deprecated
  }

  def getAllowEmptyValue: Boolean = allowEmptyValue

  def setAllowEmptyValue(allowEmptyValue: Boolean): Unit = {
    this.allowEmptyValue = allowEmptyValue
  }

  def getStyle: String = style

  def setStyle(style: String): Unit = {
    this.style = style
  }

  def getExplode: Boolean = explode

  def setExplode(explode: Boolean): Unit = {
    this.explode = explode
  }

  def getSchema: Schema = schema

  def setSchema(schema: Schema): Unit = {
    this.schema = schema
  }
}
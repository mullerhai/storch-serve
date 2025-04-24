package org.pytorch.serve.openapi

import com.google.gson.annotations.SerializedName

import java.util
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

class Schema {
  private var `type`: String = null
  private var format: String = null
  private var name: String = null
  private var required: ListBuffer[String] = new ListBuffer[String]
  private var properties: mutable.Map[String, Schema] = mutable.Map.empty
  private var items: Schema = null
  private var description: String = null
  private var example: AnyRef = null
  private var additionalProperties: Schema = null
  private var discriminator: String = null
  @SerializedName("enum")
  private var enumeration: ListBuffer[String] = new ListBuffer[String]
  @SerializedName("default") 
  private var defaultValue: String = null

  def this(`type`: String, description: String, defaultValue: String) ={
    this()
    this.`type` = `type`
    this.description = description
    this.defaultValue = defaultValue
  }

  def this(`type`: String) ={
    this(`type`, null, null)
  }

  def this(`type`: String, description: String) ={
    this(`type`, description, null)
  }

  def getType: String = `type`

  def setType(`type`: String): Unit = {
    this.`type` = `type`
  }

  def getFormat: String = format

  def setFormat(format: String): Unit = {
    this.format = format
  }

  def getName: String = name

  def setName(name: String): Unit = {
    this.name = name
  }

  def getRequired: List[String] = required.toList

  def setRequired(required: List[String]): Unit = {
    this.required.appendAll(required)
  }

  def getProperties: Map[String, Schema] = properties.toMap

  def setProperties(properties: Map[String, Schema]): Unit = {
    this.properties ++= properties
  }

  def addProperty(key: String, schema: Schema, requiredProperty: Boolean): Unit = {
    if (properties == null) properties = new mutable.LinkedHashMap[String, Schema]
    properties.put(key, schema)
    if (requiredProperty) {
      if (required == null) required = new ListBuffer[String]()
      required.append(key)
    }
  }

  def getItems: Schema = items

  def setItems(items: Schema): Unit = {
    this.items = items
  }

  def getDescription: String = description

  def setDescription(description: String): Unit = {
    this.description = description
  }

  def getExample: AnyRef = example

  def setExample(example: AnyRef): Unit = {
    this.example = example
  }

  def getAdditionalProperties: Schema = additionalProperties

  def setAdditionalProperties(additionalProperties: Schema): Unit = {
    this.additionalProperties = additionalProperties
  }

  def getDiscriminator: String = discriminator

  def setDiscriminator(discriminator: String): Unit = {
    this.discriminator = discriminator
  }

  def getEnumeration: List[String] = enumeration.toList

  def setEnumeration(enumeration: List[String]): Unit = {
    this.enumeration.appendAll(enumeration)
  }

  def getDefaultValue: String = defaultValue

  def setDefaultValue(defaultValue: String): Unit = {
    this.defaultValue = defaultValue
  }
}
package org.pytorch.serve.openapi

class PathParameter(name: String, `type`: String, defaultValue: String, description: String) extends Parameter {
//  this.name = name
//  this.description = description
  in = "path"
  required = true
  schema = new Schema(`type`, null, defaultValue)

  def this() = {
    this(null, "string", null, null)
  }

  def this(name: String, description: String)= {
    this(name, "string", null, description)
  }
}
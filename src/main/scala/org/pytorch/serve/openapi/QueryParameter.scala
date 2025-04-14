package org.pytorch.serve.openapi

class QueryParameter(name: String, `type`: String, defaultValue: String, required: Boolean, description: String) extends Parameter {
//  this.name = name
//  this.description = description
  in = "query"
//  this.required = required
  schema = new Schema(`type`, null, defaultValue)

  def this()= {
    this(null, "string", null, false, null)
  }

  def this(name: String, description: String)= {
    this(name, "string", null, false, description)
  }

  def this(name: String, `type`: String, description: String)= {
    this(name, `type`, null, false, description)
  }

  def this(name: String, `type`: String, defaultValue: String, description: String)= {
    this(name, `type`, defaultValue, false, description)
  }
}
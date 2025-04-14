package org.pytorch.serve.metrics

import com.google.gson.annotations.SerializedName
case class Dimension (
  @SerializedName("Name")
  var name :String,
  @SerializedName("Value")
   var value : String)
//class Dimension {
//  @SerializedName("Name") 
//  private var name = null
//  @SerializedName("Value") 
//  private var value = null
//
//  def this(name: String, value: String) ={
//    this()
//    this.name = name
//    this.value = value
//  }
//
//  def getName: String = name
//
//  def setName(name: String): Unit = {
//    this.name = name
//  }
//
//  def getValue: String = value
//
//  def setValue(value: String): Unit = {
//    this.value = value
//  }
//}
package org.pytorch.serve.ensemble

class NodeOutput( var nodeName: String,  var data: AnyRef) {
  def getNodeName: String = nodeName

  def setNodeName(nodeName: String): Unit = {
    this.nodeName = nodeName
  }

  def getData: AnyRef = data

  def setData(data: AnyRef): Unit = {
    this.data = data
  }
}
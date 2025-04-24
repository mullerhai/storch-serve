package org.pytorch.serve.openapi

import java.util
import scala.collection.mutable.ListBuffer

class Path {
  private var get: Operation = null
  private var put: Operation = null
  private var post: Operation = null
  private var head: Operation = null
  private var delete: Operation = null
  private var patch: Operation = null
  private var options: Operation = null
  private var parameters: ListBuffer[Parameter] = new ListBuffer[Parameter]

  def getGet: Operation = get

  def setGet(get: Operation): Unit = {
    this.get = get
  }

  def getPut: Operation = put

  def setPut(put: Operation): Unit = {
    this.put = put
  }

  def getPost: Operation = post

  def setPost(post: Operation): Unit = {
    this.post = post
  }

  def getHead: Operation = head

  def setHead(head: Operation): Unit = {
    this.head = head
  }

  def getDelete: Operation = delete

  def setDelete(delete: Operation): Unit = {
    this.delete = delete
  }

  def getPatch: Operation = patch

  def setPatch(patch: Operation): Unit = {
    this.patch = patch
  }

  def getOptions: Operation = options

  def setOptions(options: Operation): Unit = {
    this.options = options
  }

  def getParameters: List[Parameter] = parameters.toList

  def setParameters(parameters: List[Parameter]): Unit = {
    this.parameters.appendAll(parameters)
  }
}
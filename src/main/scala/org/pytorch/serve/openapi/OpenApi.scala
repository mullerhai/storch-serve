package org.pytorch.serve.openapi

import java.util

class OpenApi {
  private var openapi = "3.0.1"
  private var info: Info = null
  private var paths: util.Map[String, Path] = null

  def getOpenapi: String = openapi

  def setOpenapi(openapi: String): Unit = {
    this.openapi = openapi
  }

  def getInfo: Info = info

  def setInfo(info: Info): Unit = {
    this.info = info
  }

  def getPaths: util.Map[String, Path] = paths

  def setPaths(paths: util.Map[String, Path]): Unit = {
    this.paths = paths
  }

  def addPath(url: String, path: Path): Unit = {
    if (paths == null) paths = new util.LinkedHashMap[String, Path]
    paths.put(url, path)
  }
}
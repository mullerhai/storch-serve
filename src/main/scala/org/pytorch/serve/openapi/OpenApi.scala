package org.pytorch.serve.openapi

import java.util
import scala.collection.mutable

class OpenApi {
  private var openapi = "3.0.1"
  private var info: Info = null
  private var paths: mutable.Map[String, Path] = mutable.Map.empty

  def getOpenapi: String = openapi

  def setOpenapi(openapi: String): Unit = {
    this.openapi = openapi
  }

  def getInfo: Info = info

  def setInfo(info: Info): Unit = {
    this.info = info
  }

  def getPaths: mutable.Map[String, Path] = paths

  def setPaths(paths: mutable.Map[String, Path]): Unit = {
    this.paths = paths
  }

  def addPath(url: String, path: Path): Unit = {
    if (paths == null) paths = new mutable.LinkedHashMap[String, Path]
    paths.put(url, path)
  }
}
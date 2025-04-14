package org.pytorch.serve.wlm

import scala.annotation.targetName
import scala.jdk.CollectionConverters.*

class ModelVersionName(private var modelName: String, private var version: String) {
  def getModelName: String = modelName

  def getVersion: String = version

  def getVersionedModelName: String = getModelName + "_" + getVersion

  @targetName("hashCode")
  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + (if (modelName == null) 0
    else modelName.hashCode)
    result = prime * result + (if (version == null) 0
    else version.hashCode)
    result
  }

//  @targetName("equals") // todo need do
//  override def equals(obj: AnyRef): Boolean = {
//    if (this eq obj) return true
//    if (obj == null) return false
//    if (!obj.isInstanceOf[ModelVersionName]) return false
//    val mvn = obj.asInstanceOf[ModelVersionName]
//    (mvn.getModelName == this.modelName) && (mvn.getVersion == this.version)
//  }
}
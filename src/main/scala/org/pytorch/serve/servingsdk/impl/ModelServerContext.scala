package org.pytorch.serve.servingsdk.impl

import org.pytorch.serve.servingsdk.{Context, Model as Mo}
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.wlm.{ModelManager, Model as Mod}

import java.util
import java.util.Properties
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
class ModelServerContext extends Context {
  override def getConfig: Properties = ConfigManager.getInstance.getConfiguration

  override def getModels: util.Map[String, Mo] = {
    val r = new mutable.HashMap[String, Mo]
    ModelManager.getInstance.getDefaultModels
      .foreach((k: String, v: Mod) =>
        r.put(k, new ModelServerModel(v)))
    r.toMap.asJava
  }
}
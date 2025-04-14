package org.pytorch.serve.servingsdk.impl

import java.util
import java.util.Properties
import org.pytorch.serve.servingsdk.Context
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.wlm.{ModelManager}
import org.pytorch.serve.servingsdk.Model as Mo
import org.pytorch.serve.wlm.Model as Mod
class ModelServerContext extends Context {
  override def getConfig: Properties = ConfigManager.getInstance.getConfiguration

  override def getModels: util.Map[String, Mo] = {
    val r = new util.HashMap[String, Mo]
    ModelManager.getInstance.getDefaultModels
      .forEach((k: String, v: Mod) =>
        r.put(k, new ModelServerModel(v)))
    r
  }
}
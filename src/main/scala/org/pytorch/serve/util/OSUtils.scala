package org.pytorch.serve.util

import scala.jdk.CollectionConverters._

object OSUtils {
  def getKillCmd: String = {
    val operatingSystem = System.getProperty("os.name").toLowerCase
    var killCMD: String = null
    if (operatingSystem.indexOf("win") >= 0) killCMD = "taskkill /f /PID %s"
    else killCMD = "kill -9 %s"
    killCMD
  }
}

final class OSUtils private {
}
package org.pytorch.serve.util.messages

import org.pytorch.serve.archive.model.Manifest
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.wlm.Model
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, IOException}
import java.nio.file.{Files, Path, Paths}
import java.util
import java.util.regex.Pattern
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

object EnvironmentUtils {
  private val logger = LoggerFactory.getLogger(classOf[EnvironmentUtils])
  private val configManager = ConfigManager.getInstance

  def getEnvString(cwd: String, modelPath: String, handler: String): Array[String] = {
    val envList = new ListBuffer[String]
    val pythonPath = new StringBuilder
    val blackList = configManager.getBlacklistPattern
    if (handler != null && handler.contains(":")) {
      var handlerFile = handler
      handlerFile = handler.split(":")(0)
      if (handlerFile.contains("/")) handlerFile = handlerFile.substring(0, handlerFile.lastIndexOf('/'))
      pythonPath.append(handlerFile).append(File.pathSeparatorChar)
    }
    val environment = new mutable.HashMap[String, String] //(System.getenv)
    environment ++= System.getenv().asScala
    //    environment.putAll(configManager.getBackendConfiguration)
    environment.addAll(configManager.getBackendConfiguration)
    if (System.getenv("PYTHONPATH") != null) pythonPath.append(System.getenv("PYTHONPATH")).append(File.pathSeparatorChar)
    if (modelPath != null) {
      var modelPathCanonical = new File(modelPath)
      try modelPathCanonical = modelPathCanonical.getCanonicalFile
      catch {
        case e: IOException =>
          logger.error("Invalid model path {}", modelPath, e)
      }
      pythonPath.append(modelPathCanonical.getAbsolutePath).append(File.pathSeparatorChar)
      val dependencyPath = new File(modelPath)
      if (Files.isSymbolicLink(dependencyPath.toPath)) pythonPath.append(dependencyPath.getParentFile.getAbsolutePath).append(File.pathSeparatorChar)
    }
    if (!cwd.contains("site-packages") && !cwd.contains("dist-packages")) pythonPath.append(cwd)
    environment.put("PYTHONPATH", pythonPath.toString)

    for (entry <- environment.toList) {
      if (!blackList.matcher(entry._1).matches) envList.append(entry._1 + '=' + entry._2)
    }
    envList.toArray() //new Array[String](0)) // NOPMD
  }

  def getPythonRunTime(model: Model): String = {
    var pythonRuntime: String = null
    val runtime = model.getRuntimeType
    if (runtime eq Manifest.RuntimeType.PYTHON) {
      pythonRuntime = configManager.getPythonExecutable
      val pythonVenvRuntime = Paths.get(getPythonVenvPath(model).toString, "bin", "python")
      if (model.isUseVenv && Files.exists(pythonVenvRuntime)) pythonRuntime = pythonVenvRuntime.toString
    }
    else pythonRuntime = runtime.toString
    pythonRuntime
  }

  def getPythonVenvPath(model: Model): File = {
    var modelDir = model.getModelDir
    if (Files.isSymbolicLink(modelDir.toPath)) modelDir = modelDir.getParentFile
    val venvPath = Paths.get(modelDir.getAbsolutePath, "venv").toAbsolutePath
    venvPath.toFile
  }

  def getCppEnvString(libPath: String): Array[String] = {
    val envList = new ListBuffer[String]
    val cppPath = new StringBuilder
    val blackList = configManager.getBlacklistPattern
    val environment = new mutable.HashMap[String, String]() //(System.getenv)
    environment ++= System.getenv().asScala
    environment.addAll(configManager.getBackendConfiguration)
    cppPath.append(libPath)
    val os = System.getProperty("os.name").toLowerCase
    if (os.indexOf("win") >= 0) {
      if (System.getenv("PATH") != null) cppPath.append(File.pathSeparatorChar).append(System.getenv("PATH"))
      environment.put("PATH", cppPath.toString)
    }
    else if (os.indexOf("mac") >= 0) {
      if (System.getenv("DYLD_LIBRARY_PATH") != null) cppPath.append(File.pathSeparatorChar).append(System.getenv("DYLD_LIBRARY_PATH"))
      environment.put("DYLD_LIBRARY_PATH", cppPath.toString)
    }
    else {
      if (System.getenv("LD_LIBRARY_PATH") != null) cppPath.append(File.pathSeparatorChar).append(System.getenv("LD_LIBRARY_PATH"))
      environment.put("LD_LIBRARY_PATH", cppPath.toString)
    }

    for (entry <- environment.toList) {
      if (!blackList.matcher(entry._1).matches) envList.append(entry._1 + '=' + entry._2)
    }
    envList.toArray() //new Array[String](0)) // NOPMD
  }
}

final class EnvironmentUtils private {
}
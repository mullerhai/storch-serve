package org.pytorch.serve.wlm

import com.google.gson.JsonObject
import org.apache.commons.io.FileUtils
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.{Manifest, ModelArchive, ModelConfig, ModelException, ModelNotFoundException, ModelVersionNotFoundException}
import org.pytorch.serve.http.messages.RegisterModelRequest
import org.pytorch.serve.http.{ConflictStatusException, InvalidModelVersionException}
import org.pytorch.serve.job.Job
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.messages.EnvironmentUtils
import org.pytorch.serve.wlm.{Model, WorkLoadManager, WorkerThread}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{BufferedReader, File, IOException, InputStreamReader}
import java.net.HttpURLConnection
import java.nio.file.{Files, Path, Paths}
import java.util
import java.util.Map.Entry
import java.util.concurrent.*
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks.{break, breakable}

object ModelManager {
  private val logger = LoggerFactory.getLogger(classOf[ModelManager])
  private var modelManager: ModelManager = null

  def init(configManager: ConfigManager, wlm: WorkLoadManager): Unit = {
    modelManager = new ModelManager(configManager, wlm)
  }

  def getInstance: ModelManager = modelManager
}

final class ModelManager private( val configManager: ConfigManager,  val wlm: WorkLoadManager) {

  final private var modelsNameMap: TrieMap[String, ModelVersionedRefs] = new TrieMap[String, ModelVersionedRefs]
  final private var startupModels: mutable.HashSet[String] = new mutable.HashSet[String]
  final  var scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

  def getScheduler: ScheduledExecutorService = scheduler

  @throws[ModelException]
  @throws[IOException]
  @throws[InterruptedException]
  @throws[DownloadArchiveException]
  def registerModel(url: String, defaultModelName: String): ModelArchive = registerModel(url, null, null, null, -1 * RegisterModelRequest.DEFAULT_BATCH_SIZE, -1 * RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY, configManager.getDefaultResponseTimeout, configManager.getDefaultStartupTimeout, defaultModelName, false, false, false)

  @throws[ModelException]
  @throws[IOException]
  @throws[InterruptedException]
  @throws[DownloadArchiveException]
  @throws[WorkerInitializationException]
  def registerAndUpdateModel(modelName: String, modelInfo: JsonObject): Unit = {
    val defaultVersion = modelInfo.get(Model.DEFAULT_VERSION).getAsBoolean
    val url = modelInfo.get(Model.MAR_NAME).getAsString
    val archive = createModelArchive(modelName, url, null, null, modelName, false)
    val tempModel = createModel(archive, modelInfo)
    val versionId = archive.getModelVersion
    createVersionedModel(tempModel, versionId)
    setupModelVenv(tempModel)
    setupModelDependencies(tempModel)
    if (defaultVersion) ModelManager.modelManager.setDefaultVersion(modelName, versionId)
    ModelManager.logger.info("Model {} loaded.", tempModel.getModelName)
    updateModel(modelName, versionId, true)
  }

  @throws[ModelException]
  @throws[IOException]
  @throws[InterruptedException]
  @throws[DownloadArchiveException]
  def registerModel(url: String, modelName: String, runtime: Manifest.RuntimeType, handler: String, batchSize: Int, maxBatchDelay: Int, responseTimeout: Int, startupTimeout: Int, defaultModelName: String, ignoreDuplicate: Boolean, isWorkflowModel: Boolean, s3SseKms: Boolean): ModelArchive = {
    var archive: ModelArchive = null
    if (isWorkflowModel && url == null) { // This is  a workflow function
      val manifest = new Manifest
      manifest.getModel.setVersion("1.0")
      manifest.getModel.setModelVersion("1.0")
      manifest.getModel.setModelName(modelName)
      manifest.getModel.setHandler(new File(handler).getName)
      manifest.getModel.setEnvelope(configManager.getTsServiceEnvelope)
      val f = new File(handler.substring(0, handler.lastIndexOf(':')))
      archive = new ModelArchive(manifest, url, f.getParentFile, true)
    }
    else archive = createModelArchive(modelName, url, handler, runtime, defaultModelName, s3SseKms)
    val tempModel = createModel(archive, batchSize, maxBatchDelay, responseTimeout, startupTimeout, isWorkflowModel)
    val versionId = archive.getModelVersion
    try createVersionedModel(tempModel, versionId)
    catch {
      case e: ConflictStatusException =>
        if (!ignoreDuplicate) throw e
    }
    setupModelVenv(tempModel)
    setupModelDependencies(tempModel)
    ModelManager.logger.info("Model {} loaded.", tempModel.getModelName)
    archive
  }

  @throws[ModelException]
  @throws[IOException]
  @throws[DownloadArchiveException]
  private def createModelArchive(modelName: String, url: String, handler: String, runtime: Manifest.RuntimeType, defaultModelName: String, s3SseKms: Boolean) = {
    val archive = ModelArchive.downloadModel(configManager.getAllowedUrls, configManager.getModelStore, url, s3SseKms)
    val model = archive.getManifest.getModel
    if (modelName == null || modelName.isEmpty) if (archive.getModelName == null || archive.getModelName.isEmpty) model.setModelName(defaultModelName)
    else model.setModelName(modelName)
    if (runtime != null) archive.getManifest.setRuntime(runtime)
    if (handler != null) model.setHandler(handler)
    else if (archive.getHandler == null || archive.getHandler.isEmpty) model.setHandler(configManager.getTsDefaultServiceHandler)
    model.setEnvelope(configManager.getTsServiceEnvelope)
    if (model.getModelVersion == null) model.setModelVersion("1.0")
    archive.validate()
    archive
  }

  def unregisterModel(modelName: String, versionIdTmp: String, isCleanUp: Boolean): Int = {
    val vmodel = modelsNameMap.get(modelName)
    if (vmodel == null) {
      ModelManager.logger.warn("Model not found: " + modelName)
      return HttpURLConnection.HTTP_NOT_FOUND
    }
    var versionId = if (versionIdTmp == null) then vmodel.get.getDefaultVersion else versionIdTmp
    var model: Model = null
    var httpResponseStatus = 0
    try {
      model = vmodel.get.removeVersionModel(versionId)
      model.setMinWorkers(0)
      model.setMaxWorkers(0)
      val futureStatus = wlm.modelChanged(model, false, isCleanUp)
      httpResponseStatus = futureStatus.get
      // Only continue cleaning if resource cleaning succeeded
      if (httpResponseStatus == HttpURLConnection.HTTP_OK) {
        model.getModelArchive.clean()
        startupModels.remove(modelName)
        ModelManager.logger.info("Model {} unregistered.", modelName)
      }
      else {
        if (versionId == null) versionId = vmodel.get.getDefaultVersion
        vmodel.get.addVersionModel(model, versionId)
      }
      if (vmodel.get.getAllVersions.size == 0) modelsNameMap.remove(modelName)
      if (!isCleanUp && model.getModelUrl != null) ModelArchive.removeModel(configManager.getModelStore, model.getModelUrl)
    } catch {
      case e: ModelVersionNotFoundException =>
        ModelManager.logger.warn("Model {} version {} not found.", modelName, versionId)
        httpResponseStatus = HttpURLConnection.HTTP_BAD_REQUEST
      case e: InvalidModelVersionException =>
        ModelManager.logger.warn("Cannot remove default version {} for model {}", versionId, modelName)
        httpResponseStatus = HttpURLConnection.HTTP_FORBIDDEN
      case e1@(_: ExecutionException | _: InterruptedException) =>
        ModelManager.logger.warn("Process was interrupted while cleaning resources.")
        httpResponseStatus = HttpURLConnection.HTTP_INTERNAL_ERROR
    }
    httpResponseStatus
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  def setDefaultVersion(modelName: String, newModelVersion: String): Unit = {
    val vmodel = modelsNameMap.get(modelName)
    if (vmodel == null) {
      ModelManager.logger.warn("Model not found: " + modelName)
      throw new ModelNotFoundException("Model not found: " + modelName)
    }
    vmodel.get.setDefaultVersion(newModelVersion)
  }

  private def isValidDependencyPath(dependencyPath: File): Boolean = {
    if (dependencyPath.toPath.normalize.startsWith(FileUtils.getTempDirectory.toPath.normalize)) return true
    false
  }

  private def createModel(archive: ModelArchive, batchSizez: Int, maxBatchDelayz: Int, responseTimeoutz: Int, startupTimeoutz: Int, isWorkflowModel: Boolean) = {
    val model = new Model(archive, configManager.getJobQueueSize)
    var batchSize = batchSizez
    var maxBatchDelay = maxBatchDelayz
    var responseTimeout = responseTimeoutz
    var startupTimeout = startupTimeoutz
    if (batchSize == -1 * RegisterModelRequest.DEFAULT_BATCH_SIZE) if (archive.getModelConfig != null) {
      val marBatchSize = archive.getModelConfig.getBatchSize
      batchSize = if (marBatchSize > 0) marBatchSize
      else configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.BATCH_SIZE, RegisterModelRequest.DEFAULT_BATCH_SIZE)
    }
    else batchSize = configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.BATCH_SIZE, RegisterModelRequest.DEFAULT_BATCH_SIZE)
    model.setBatchSize(batchSize)
    if (maxBatchDelay == -1 * RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY) if (archive.getModelConfig != null) {
      val marMaxBatchDelay = archive.getModelConfig.getMaxBatchDelay
      maxBatchDelay = if (marMaxBatchDelay > 0) marMaxBatchDelay
      else configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.MAX_BATCH_DELAY, RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY)
    }
    else maxBatchDelay = configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.MAX_BATCH_DELAY, RegisterModelRequest.DEFAULT_MAX_BATCH_DELAY)
    model.setMaxBatchDelay(maxBatchDelay)
    if (archive.getModelConfig != null) {
      val marResponseTimeout = archive.getModelConfig.getResponseTimeout
      val marStartupTimeout = archive.getModelConfig.getStartupTimeout
      responseTimeout = if (marResponseTimeout > 0) marResponseTimeout
      else configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.RESPONSE_TIMEOUT, responseTimeout)
      startupTimeout = if (marStartupTimeout > 0) marStartupTimeout
      else configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.STARTUP_TIMEOUT, startupTimeout)
    }
    else {
      responseTimeout = configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.RESPONSE_TIMEOUT, responseTimeout)
      startupTimeout = configManager.getJsonIntValue(archive.getModelName, archive.getModelVersion, Model.STARTUP_TIMEOUT, startupTimeout)
    }
    model.setResponseTimeout(responseTimeout)
    model.setStartupTimeout(startupTimeout)
    model.setWorkflowModel(isWorkflowModel)
    model.setRuntimeType(configManager.getJsonRuntimeTypeValue(archive.getModelName, archive.getModelVersion, Model.RUNTIME_TYPE, archive.getManifest.getRuntime))
    model
  }

  private def createModel(archive: ModelArchive, modelInfo: JsonObject) = {
    val model = new Model(archive, configManager.getJobQueueSize)
    model.setModelState(modelInfo)
    model.setWorkflowModel(false)
    model
  }

  def getDefaultModels: Map[String, Model] = getDefaultModels(false)

  def unregisterModel(modelName: String, versionId: String): Int = unregisterModel(modelName, versionId, false)

  def getDefaultModels(skipFuntions: Boolean): Map[String, Model] = {
    val defModelsMap = new TrieMap[String, Model]
    //    import scala.collection.JavaConversions._
    for (key <- modelsNameMap.keySet) {
      val mvr = modelsNameMap.get(key)
      if (mvr != null) {
        val defaultModel = mvr.get.getDefaultModel
        if (defaultModel != null) {
          breakable(
            if (skipFuntions && defaultModel.getModelUrl == null) break() //continue //todo: continue is not supported

          )

          defModelsMap.put(key, defaultModel)
        }
      }
    }
    defModelsMap.toMap
  }

  def getWorkers(modelVersionName: ModelVersionName): List[WorkerThread] = wlm.getWorkers(modelVersionName)

  @throws[ModelVersionNotFoundException]
  @throws[WorkerInitializationException]
  private def updateModel(modelName: String, versionId: String, isStartup: Boolean) : CompletableFuture[Integer] = {
    val model = getVersionModel(modelName, versionId)
    updateModel(modelName, versionId, model.getMinWorkers, model.getMaxWorkers, isStartup, false)
  }

  @throws[ModelVersionNotFoundException]
  @throws[WorkerInitializationException]
  def updateModel(modelName: String, versionId: String, minWorkersTmp: Int, maxWorkersTmp: Int, isStartup: Boolean, isCleanUp: Boolean): CompletableFuture[Integer] = {
    val model = getVersionModel(modelName, versionId)
    var minWorkers = minWorkersTmp
    var maxWorkers = maxWorkersTmp
    if (model == null) throw new ModelVersionNotFoundException("Model version: " + versionId + " does not exist for model: " + modelName)
    if (model.getParallelLevel > 0 && (model.getDeviceType eq ModelConfig.DeviceType.GPU)) {
      /**
       * Current capacity check for LMI is based on single node. TODO: multiple nodes check
       * will be based on --proc-per-node + numCores.
       */
      val capacity = model.getNumCores / model.getParallelLevel
      if (capacity == 0) {
        ModelManager.logger.error("there are no enough gpu devices to support this parallelLever: {}", model.getParallelLevel)
        throw new WorkerInitializationException("No enough gpu devices for model:" + modelName + " parallelLevel:" + model.getParallelLevel)
      }
      else {
        minWorkers = if (minWorkers > capacity) then capacity else minWorkers
        maxWorkers = if (maxWorkers > capacity) then capacity else maxWorkers
        ModelManager.logger.info("model {} set minWorkers: {}, maxWorkers: {} for parallelLevel: {} ", modelName, minWorkers, maxWorkers, model.getParallelLevel)
      }
    }
    model.setMinWorkers(minWorkers)
    model.setMaxWorkers(maxWorkers)
    ModelManager.logger.debug("updateModel: {}, count: {}", modelName, minWorkers)
    wlm.modelChanged(model, isStartup, isCleanUp)
  }

  def getWorkers: Map[Integer, WorkerThread] = wlm.getWorkers

  @throws[ModelVersionNotFoundException]
  @throws[WorkerInitializationException]
  def updateModel(modelName: String, versionId: String, minWorkers: Int, maxWorkers: Int): CompletableFuture[Integer] = updateModel(modelName, versionId, minWorkers, maxWorkers, false, false)

  def scaleRequestStatus(modelName: String, versionId: String): Boolean = {
    val model = modelsNameMap.get(modelName).get.getVersionModel(versionId)
    var numWorkers = 0
    if (model != null) numWorkers = wlm.getNumRunningWorkers(model.getModelVersionName)
    model == null || model.getMinWorkers <= numWorkers
  }

  def getStartupModels: mutable.HashSet[String] = startupModels

  @throws[ModelVersionNotFoundException]
  def getModel(modelName: String, versionId: String): Model = {
    val vmodel = modelsNameMap.get(modelName)
    if (vmodel == null) return null
    val model = vmodel.get.getVersionModel(versionId)
    if (model == null) throw new ModelVersionNotFoundException("Model version: " + versionId + " does not exist for model: " + modelName)
    else model
  }

  @throws[ModelNotFoundException]
  def getAllModelVersions(modelName: String): Map[String, Model] = {
    val vmodel = modelsNameMap.get(modelName)
    if (vmodel == null) throw new ModelNotFoundException("Model not found: " + modelName)
    vmodel.get.getAllVersions
  }

  @throws[ModelNotFoundException]
  @throws[ModelVersionNotFoundException]
  def addJob(job: Job): Boolean = {
    val modelName = job.getModelName
    val versionId = job.getModelVersion
    val model = getModel(modelName, versionId)
    if (model == null) throw new ModelNotFoundException("Model not found: " + modelName)
    if (wlm.hasNoWorker(model.getModelVersionName)) return false
    model.addJob(job)
  }

  def getAllModels: mutable.Map[String, ModelVersionedRefs] = modelsNameMap //.entrySet.asScala

  def submitTask(runnable: Runnable): Unit = {
    wlm.scheduleAsync(runnable)
  }

  @throws[IOException]
  @throws[InterruptedException]
  @throws[ModelException]
  private def setupModelVenv(model: Model): Unit = {
    if (!model.isUseVenv) return
    val venvPath = EnvironmentUtils.getPythonVenvPath(model)
    val commandParts = new ListBuffer[String]
    commandParts.append(configManager.getPythonExecutable)
    commandParts.append(Paths.get(configManager.getModelServerHome, "ts", "utils", "setup_model_venv.py").toAbsolutePath.toString)
    commandParts.append(venvPath.toString)
    val processBuilder = new ProcessBuilder(commandParts.toArray *)
    if (isValidDependencyPath(venvPath)) processBuilder.directory(venvPath.getParentFile)
    else throw new ModelException("Invalid python venv path for model " + model.getModelName + ": " + venvPath.toString)
    val environment = processBuilder.environment
    val envp = EnvironmentUtils.getEnvString(configManager.getModelServerHome, model.getModelDir.getAbsolutePath, null)
    for (envVar <- envp) {
      val parts = envVar.split("=", 2)
      if (parts.length == 2) environment.put(parts(0), parts(1))
    }
    processBuilder.redirectErrorStream(true)
    val process = processBuilder.start
    val exitCode = process.waitFor
    var line: String = null
    val outputString = new StringBuilder
    val brdr = new BufferedReader(new InputStreamReader(process.getInputStream))
    while (brdr.readLine != null) {
      line = brdr.readLine
      outputString.append(line + "\n")
    }
    if (exitCode == 0) ModelManager.logger.info("Created virtual environment for model {}: {}", model.getModelName, venvPath.toString)
    else {
      ModelManager.logger.error("Virtual environment creation for model {} at {} failed:\n{}", model.getModelName, venvPath.toString, outputString.toString)
      throw new ModelException("Virtual environment creation failed for model " + model.getModelName)
    }
  }

  @throws[IOException]
  @throws[InterruptedException]
  @throws[ModelException]
  private def setupModelDependencies(model: Model): Unit = {
    val requirementsFile = model.getModelArchive.getManifest.getModel.getRequirementsFile
    if (!configManager.getInstallPyDepPerModel || requirementsFile == null) return
    val pythonRuntime = EnvironmentUtils.getPythonRunTime(model)
    val requirementsFilePath = Paths.get(model.getModelDir.getAbsolutePath, requirementsFile).toAbsolutePath
    val commandParts = new ListBuffer[String]
    val processBuilder = new ProcessBuilder
    if (model.isUseVenv) {
      if (!isValidDependencyPath(Paths.get(pythonRuntime).toFile)) throw new ModelException("Invalid python venv runtime path for model " + model.getModelName + ": " + pythonRuntime)
      processBuilder.directory(EnvironmentUtils.getPythonVenvPath(model).getParentFile)
      commandParts.append(pythonRuntime)
      commandParts.append("-m")
      commandParts.append("pip")
      commandParts.append("install")
      commandParts.append("-U")
      commandParts.append("--upgrade-strategy")
      commandParts.append("only-if-needed")
      commandParts.append("-r")
      commandParts.append(requirementsFilePath.toString)
    }
    else {
      var dependencyPath = model.getModelDir
      if (Files.isSymbolicLink(dependencyPath.toPath)) dependencyPath = dependencyPath.getParentFile
      dependencyPath = dependencyPath.getAbsoluteFile
      if (!isValidDependencyPath(dependencyPath)) throw new ModelException("Invalid 3rd party package installation path " + dependencyPath.toString)
      processBuilder.directory(dependencyPath)
      commandParts.append(pythonRuntime)
      commandParts.append("-m")
      commandParts.append("pip")
      commandParts.append("install")
      commandParts.append("-U")
      commandParts.append("-t")
      commandParts.append(dependencyPath.toString)
      commandParts.append("-r")
      commandParts.append(requirementsFilePath.toString)
    }
    processBuilder.command(commandParts.toArray *)
    val envp = EnvironmentUtils.getEnvString(configManager.getModelServerHome, model.getModelDir.getAbsolutePath, null)
    val environment = processBuilder.environment
    for (envVar <- envp) {
      val parts = envVar.split("=", 2)
      if (parts.length == 2) environment.put(parts(0), parts(1))
    }
    processBuilder.redirectErrorStream(true)
    val process = processBuilder.start
    val exitCode = process.waitFor
    var line: String = null
    val outputString = new StringBuilder
    val brdr = new BufferedReader(new InputStreamReader(process.getInputStream))
    while (brdr.readLine != null) {
      line = brdr.readLine
      outputString.append(line + "\n")
    }
    if (exitCode == 0) ModelManager.logger.info("Installed custom pip packages for model {}", model.getModelName)
    else {
      ModelManager.logger.error("Custom pip package installation failed for model {}:\n{}", model.getModelName, outputString.toString)
      throw new ModelException("Custom pip package installation failed for model " + model.getModelName)
    }
  }

  @throws[ModelVersionNotFoundException]
  @throws[ConflictStatusException]
  private def createVersionedModel(model: Model, versionId: String): Unit = {
    var modelVersionRef = modelsNameMap.get(model.getModelName)
    if (modelVersionRef == null) modelVersionRef = Some(new ModelVersionedRefs)
    modelVersionRef.get.addVersionModel(model, versionId)
    modelsNameMap.putIfAbsent(model.getModelName, modelVersionRef.get)
  }

  private def getVersionModel(modelName: String, versionId: String) = {
    val vmodel = modelsNameMap.get(modelName)
    if (vmodel == null) throw new AssertionError("Model not found: " + modelName)
    vmodel.get.getVersionModel(versionId)
  }

  def getNumRunningWorkers(modelVersionName: ModelVersionName): Int = wlm.getNumRunningWorkers(modelVersionName)

  def getNumHealthyWorkers(modelVersionName: ModelVersionName): Int = wlm.getNumHealthyWorkers(modelVersionName)
}
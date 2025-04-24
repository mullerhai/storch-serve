package org.pytorch.serve.snapshot

import com.google.gson.JsonObject
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.{ModelException, ModelNotFoundException}
import org.pytorch.serve.servingsdk.snapshot.{Snapshot, SnapshotSerializer}
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.wlm.{Model, ModelManager, WorkerInitializationException}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, IOException}
import java.text.SimpleDateFormat
import java.util
import java.util.Date
import java.util.Map.Entry
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.control.Breaks.{break, breakable}
object SnapshotManager {
  private val logger = LoggerFactory.getLogger(classOf[SnapshotManager])
  private var snapshotManager: SnapshotManager = null

  def init(configManager: ConfigManager): Unit = {
    snapshotManager = new SnapshotManager(configManager)
  }

  def getInstance: SnapshotManager = snapshotManager
}

final class SnapshotManager private(private var configManager: ConfigManager) {

  private var modelManager: ModelManager =  ModelManager.getInstance
  private var snapshotSerializer: SnapshotSerializer = SnapshotSerializerFactory.getSerializer

  private def saveSnapshot(snapshotName: String): Unit = {
    if (configManager.isSnapshotDisabled) return
    val defModels = modelManager.getDefaultModels(true)
    val modelNameMap = new mutable.HashMap[String, Map[String, JsonObject]]
    try {
      var modelCount = 0

      for (m <- defModels.toSeq) {
        breakable(
          if (m._2.isWorkflowModel) break() //todo: continue is not supported
        )

        val versionModels = modelManager.getAllModelVersions(m._1)
        val modelInfoMap = new mutable.HashMap[String, JsonObject]

        for (versionedModel <- versionModels.toSeq) {
          val version = String.valueOf(versionedModel._1)
          val isDefaultVersion = m._2.getVersion == versionedModel._2.getVersion
          modelInfoMap.put(version, versionedModel._2.getModelState(isDefaultVersion))
          modelCount += 1
        }
        modelNameMap.put(m._1, modelInfoMap.toMap)
      }
      val snapshot = new Snapshot(snapshotName, modelCount)
      snapshot.setModels(modelNameMap.map((k, v) => (k, v.asJava)).asJava)
      snapshotSerializer.saveSnapshot(snapshot, configManager.getConfiguration)
    } catch {
      case e: ModelNotFoundException =>
        SnapshotManager.logger.error("Model not found while saving snapshot {}", snapshotName)
      case e: IOException =>
        SnapshotManager.logger.error("Error while saving snapshot to file {}", snapshotName)
    }
  }

  def saveSnapshot(): Unit = {
    saveSnapshot(getSnapshotName("snapshot"))
  }

  def saveStartupSnapshot(): Unit = {
    saveSnapshot(getSnapshotName("startup"))
  }

  def saveShutdownSnapshot(): Unit = {
    saveSnapshot(getSnapshotName("shutdown"))
  }

  @SuppressWarnings(Array("PMD"))
  @throws[SnapshotReadException]
  def getSnapshot(snapshotName: String): Snapshot = try snapshotSerializer.getSnapshot(snapshotName)
  catch {
    case e: IOException =>
      throw new SnapshotReadException("Error while retrieving snapshot details. Cause : " + e.getCause)
  }

  @throws[InvalidSnapshotException]
  @throws[IOException]
  def restore(modelSnapshot: String): Unit = {
    SnapshotManager.logger.info("Started restoring models from snapshot {}", modelSnapshot)
    val snapshot = snapshotSerializer.getSnapshot(modelSnapshot)
    // Validate snapshot
    validate(snapshot)
    // Init. models
    initModels(snapshot)
  }

  private def initModels(snapshot: Snapshot): Unit = {
    try {
      val models = snapshot.getModels
      if (snapshot.getModelCount <= 0) {
        SnapshotManager.logger.warn("Model snapshot is empty. Starting TorchServe without initial models.")
        return
      }
//      import scala.collection.JavaConversions._
      for (modelMap <- models.entrySet.asScala) {
        val modelName = modelMap.getKey
//        import scala.collection.JavaConversions._
        for (versionModel <- modelMap.getValue.entrySet.asScala) {
          val modelInfo = versionModel.getValue
          modelManager.registerAndUpdateModel(modelName, modelInfo)
        }
      }
    } catch {
      case e: IOException =>
        SnapshotManager.logger.error("Error while retrieving snapshot details. Details: {}", e.getMessage)
      case e@(_: ModelException | _: InterruptedException | _: DownloadArchiveException | _: WorkerInitializationException) =>
        SnapshotManager.logger.error("Error while registering model. Details: {}", e.getMessage)
    }
  }

  @throws[IOException]
  @throws[InvalidSnapshotException]
  private def validate(snapshot: Snapshot) = {
    SnapshotManager.logger.info("Validating snapshot {}", snapshot.getName)
    val modelStore = configManager.getModelStore
    val models = snapshot.getModels
//    import scala.collection.JavaConversions._
    for (modelMap <- models.entrySet.asScala) {
      val modelName = modelMap.getKey
//      import scala.collection.JavaConversions._
      for (versionModel <- modelMap.getValue.entrySet.asScala) {
        val versionId = versionModel.getKey
        val marName = versionModel.getValue.get(Model.MAR_NAME).getAsString
        val marFile = new File(modelStore + "/" + marName)
        if (!marFile.exists) {
          SnapshotManager.logger.error("Model archive file for model {}, version {} not found in model store", modelName, versionId)
          throw new InvalidSnapshotException("Model archive file for model :" + modelName + ", version :" + versionId + " not found in model store")
        }
      }
    }
    SnapshotManager.logger.info("Snapshot {} validated successfully", snapshot.getName)
    true
  }

  private def getSnapshotName(snapshotType: String) = new SimpleDateFormat("yyyyMMddHHmmssSSS'-" + snapshotType + ".cfg'").format(new Date)
}
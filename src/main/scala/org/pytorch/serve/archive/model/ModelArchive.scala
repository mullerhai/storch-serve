package org.pytorch.serve.archive.model

import org.apache.commons.io.FileUtils
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.utils.{ArchiveUtils, InvalidArchiveURLException, ZipUtils}
import org.slf4j.{Logger, LoggerFactory}

import java.io.{File, IOException, InputStream}
import java.nio.file.{FileAlreadyExistsException, Files}
import java.util
import scala.collection.mutable.ListBuffer

object ModelArchive {
  private val logger = LoggerFactory.getLogger(classOf[ModelArchive])
  private val MANIFEST_FILE = "MANIFEST.json"

  @throws[ModelException]
  @throws[FileAlreadyExistsException]
  @throws[IOException]
  @throws[DownloadArchiveException]
  def downloadModel(allowedUrls: List[String], modelStore: String, url: String): ModelArchive = downloadModel(allowedUrls, modelStore, url, false)

  @throws[ModelException]
  @throws[FileAlreadyExistsException]
  @throws[IOException]
  @throws[DownloadArchiveException]
  def downloadModel(allowedUrls: List[String], modelStore: String, url: String, s3SseKmsEnabled: Boolean): ModelArchive = {
    if (modelStore == null) throw new ModelNotFoundException("Model store has not been configured.")
    if (url == null || url.isEmpty) throw new ModelNotFoundException("empty url")
    if (url.contains("..")) throw new ModelNotFoundException("Relative path is not allowed in url: " + url)
    val marFileName = ArchiveUtils.getFilenameFromUrl(url)
    val modelLocation = new File(modelStore, marFileName)
    try ArchiveUtils.downloadArchive(allowedUrls, modelLocation, marFileName, url, s3SseKmsEnabled)
    catch {
      case e: InvalidArchiveURLException =>
        throw new ModelNotFoundException(e.getMessage) // NOPMD
    }
    if (modelLocation.isFile) try {
      val is = Files.newInputStream(modelLocation.toPath)
      try {
        var unzipDir: File = null
        if (modelLocation.getName.endsWith(".mar")) unzipDir = ZipUtils.unzip(is, null, "models", true)
        else unzipDir = ZipUtils.unzip(is, null, "models", false)
        return load(url, unzipDir, true)
      } finally if (is != null) is.close()
    }
    val tempDir = ZipUtils.createTempDir(null, "models")
    logger.info("createTempDir {}", tempDir.getAbsolutePath)
    val directory = new File(url)
    if (directory.isDirectory) {
      // handle the case that the input url is a directory.
      // the input of url is "/xxx/model_store/modelXXX" or
      // "xxxx/yyyyy/modelXXX".
      val fileList = directory.listFiles
      if (fileList.length == 1 && fileList(0).isDirectory) {
        // handle the case that a model tgz file
        // has root dir after decompression on SageMaker
        val targetLink = ZipUtils.createSymbolicDir(fileList(0), tempDir)
        logger.info("createSymbolicDir {}", targetLink.getAbsolutePath)
        return load(url, targetLink, false)
      }
      val targetLink = ZipUtils.createSymbolicDir(directory, tempDir)
      logger.info("createSymbolicDir {}", targetLink.getAbsolutePath)
      return load(url, targetLink, false)
    }
    else if (modelLocation.exists) {
      // handle the case that "/xxx/model_store/modelXXX" is directory.
      // the input of url is modelXXX when torchserve is started
      // with snapshot or with parameter --models modelXXX
      val fileList = modelLocation.listFiles
      if (fileList.length == 1 && fileList(0).isDirectory) {
        // handle the case that a model tgz file
        // has root dir after decompression on SageMaker
        val targetLink = ZipUtils.createSymbolicDir(fileList(0), tempDir)
        logger.info("createSymbolicDir {}", targetLink.getAbsolutePath)
        return load(url, targetLink, false)
      }
      val targetLink = ZipUtils.createSymbolicDir(modelLocation, tempDir)
      logger.info("createSymbolicDir {}", targetLink.getAbsolutePath)
      return load(url, targetLink, false)
    }
    throw new ModelNotFoundException("Model not found at: " + url)
  }

  @throws[InvalidModelException]
  @throws[IOException]
  private def load(url: String, dir: File, extracted: Boolean) = {
    var failed = true
    try {
      val manifestFile = new File(dir, "MAR-INF/" + MANIFEST_FILE)
      var manifest: Manifest = null
      if (manifestFile.exists) manifest = ArchiveUtils.readFile(manifestFile, classOf[Manifest])
      else manifest = new Manifest
      failed = false
      new ModelArchive(manifest, url, dir, extracted)
    } finally if (failed) if (Files.isSymbolicLink(dir.toPath)) FileUtils.deleteQuietly(dir.getParentFile)
    else FileUtils.deleteQuietly(dir)
  }

  def removeModel(modelStore: String, marURL: String): Unit = {
    if (ArchiveUtils.isValidURL(marURL)) {
      val marFileName = ArchiveUtils.getFilenameFromUrl(marURL)
      val modelLocation = new File(modelStore, marFileName)
      FileUtils.deleteQuietly(modelLocation)
    }
  }
}

class ModelArchive(private var manifest: Manifest, private var url: String, private var modelDir: File, private var extracted: Boolean) {
 
  private var modelConfig: ModelConfig = null

  @throws[InvalidModelException]
  def validate(): Unit = {
    val model = manifest.getModel
    try {
      if (model == null) throw new InvalidModelException("Missing Model entry in manifest file.")
      if (model.getModelName == null) throw new InvalidModelException("Model name is not defined.")
      if (model.getModelVersion == null) throw new InvalidModelException("Model version is not defined.")
      if (manifest.getRuntime == null) throw new InvalidModelException("Runtime is not defined or invalid.")
      if (manifest.getArchiverVersion == null) ModelArchive.logger.warn("Model archive version is not defined. Please upgrade to torch-model-archiver 0.2.0 or higher")
      if (manifest.getCreatedOn == null) ModelArchive.logger.warn("Model archive createdOn is not defined. Please upgrade to torch-model-archiver 0.2.0 or higher")
    } catch {
      case e: InvalidModelException =>
        clean()
        throw e
    }
  }

  def getHandler: String = manifest.getModel.getHandler

  def getManifest: Manifest = manifest

  def getUrl: String = url

  def getModelDir: File = modelDir

  def getModelName: String = manifest.getModel.getModelName

  def getModelVersion: String = manifest.getModel.getModelVersion

  def clean(): Unit = {
    if (url != null) if (Files.isSymbolicLink(modelDir.toPath)) FileUtils.deleteQuietly(modelDir.getParentFile)
    else FileUtils.deleteQuietly(modelDir)
  }

  def getModelConfig: ModelConfig = {
    if (this.modelConfig == null && manifest.getModel.getConfigFile != null) try {
      val configFile = new File(modelDir.getAbsolutePath, manifest.getModel.getConfigFile)
      val modelConfigMap = ArchiveUtils.readYamlFile(configFile)
      this.modelConfig = ModelConfig.build(modelConfigMap)
    } catch {
      case e@(_: InvalidModelException | _: IOException) =>
        ModelArchive.logger.error("Failed to parse model config file {}", manifest.getModel.getConfigFile, e)
    }
    this.modelConfig
  }
}
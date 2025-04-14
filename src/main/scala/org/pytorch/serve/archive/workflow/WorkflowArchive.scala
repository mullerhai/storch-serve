package org.pytorch.serve.archive.workflow

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util
import org.apache.commons.io.FileUtils
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.utils.ArchiveUtils
import org.pytorch.serve.archive.utils.InvalidArchiveURLException
import org.pytorch.serve.archive.utils.ZipUtils

object WorkflowArchive {
  val GSON: Gson = new GsonBuilder().setPrettyPrinting.create
  private val MANIFEST_FILE = "MANIFEST.json"

  @throws[WorkflowException]
  @throws[FileAlreadyExistsException]
  @throws[IOException]
  @throws[DownloadArchiveException]
  def downloadWorkflow(allowedUrls: util.List[String], workflowStore: String, url: String): WorkflowArchive = downloadWorkflow(allowedUrls, workflowStore, url, false)

  @throws[WorkflowException]
  @throws[FileAlreadyExistsException]
  @throws[IOException]
  @throws[DownloadArchiveException]
  def downloadWorkflow(allowedUrls: util.List[String], workflowStore: String, url: String, s3SseKmsEnabled: Boolean): WorkflowArchive = {
    if (workflowStore == null) throw new WorkflowNotFoundException("Workflow store has not been configured.")
    val warFileName = ArchiveUtils.getFilenameFromUrl(url)
    val workflowLocation = new File(workflowStore, warFileName)
    try ArchiveUtils.downloadArchive(allowedUrls, workflowLocation, warFileName, url, s3SseKmsEnabled)
    catch {
      case e: InvalidArchiveURLException =>
        throw new WorkflowNotFoundException(e.getMessage) // NOPMD
    }
    if (url.contains("..")) throw new WorkflowNotFoundException("Relative path is not allowed in url: " + url)
    if (!workflowLocation.exists) throw new WorkflowNotFoundException("Workflow not found in workflow store: " + url)
    if (workflowLocation.isFile) try {
      val is = Files.newInputStream(workflowLocation.toPath)
      try {
        var unzipDir: File = null
        if (workflowLocation.getName.endsWith(".war")) unzipDir = ZipUtils.unzip(is, null, "workflows", true)
        else unzipDir = ZipUtils.unzip(is, null, "workflows", false)
        return load(url, unzipDir, true)
      } finally if (is != null) is.close()
    }
    throw new WorkflowNotFoundException("Workflow not found at: " + url)
  }

  @throws[InvalidWorkflowException]
  @throws[IOException]
  private def load(url: String, dir: File, extracted: Boolean) = {
    var failed = true
    try {
      val manifestFile = new File(dir, "WAR-INF/" + MANIFEST_FILE)
      var manifest: Manifest = null
      if (manifestFile.exists) manifest = readFile(manifestFile, classOf[Manifest])
      else manifest = new Manifest
      failed = false
      new WorkflowArchive(manifest, url, dir, extracted)
    } finally if (extracted && failed) FileUtils.deleteQuietly(dir)
  }

  @throws[InvalidWorkflowException]
  @throws[IOException]
  private def readFile[T](file: File, `type`: Class[T]) = try {
    val r = new InputStreamReader(Files.newInputStream(file.toPath), StandardCharsets.UTF_8)
    try GSON.fromJson(r, `type`)
    catch {
      case e: JsonParseException =>
        throw new InvalidWorkflowException("Failed to parse signature.json.", e)
    } finally if (r != null) r.close()
  }

  def removeWorkflow(workflowStore: String, warURL: String): Unit = {
    if (ArchiveUtils.isValidURL(warURL)) {
      val warFileName = ArchiveUtils.getFilenameFromUrl(warURL)
      val workflowLocation = new File(workflowStore, warFileName)
      FileUtils.deleteQuietly(workflowLocation)
    }
  }
}

class WorkflowArchive(private var manifest: Manifest, private var url: String, private var workflowDir: File, private var extracted: Boolean) {
  @throws[InvalidWorkflowException]
  def validate(): Unit = {
    val workflow = manifest.getWorkflow
    try {
      if (workflow == null) throw new InvalidWorkflowException("Missing Workflow entry in manifest file.")
      if (workflow.getWorkflowName == null) throw new InvalidWorkflowException("Workflow name is not defined.")
      if (manifest.getArchiverVersion == null) throw new InvalidWorkflowException("Workflow archive version is not defined.")
      if (manifest.getCreatedOn == null) throw new InvalidWorkflowException("Workflow archive createdOn is not defined.")
    } catch {
      case e: InvalidWorkflowException =>
        clean()
        throw e
    }
  }

  def getHandler: String = manifest.getWorkflow.getHandler

  def getManifest: Manifest = manifest

  def getUrl: String = url

  def getWorkflowDir: File = workflowDir

  def getWorkflowName: String = manifest.getWorkflow.getWorkflowName

  def clean(): Unit = {
    if (url != null && extracted) then FileUtils.deleteQuietly(workflowDir)
  }
}
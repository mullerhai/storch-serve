package org.pytorch.serve.snapshot

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Comparator
import java.util.Date
import java.util.Optional
import java.util.Properties
import org.apache.commons.io.FileUtils
import org.pytorch.serve.servingsdk.snapshot.Snapshot
import org.pytorch.serve.servingsdk.snapshot.SnapshotSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._

object FSSnapshotSerializer {
  private val MODEL_SNAPSHOT = "model_snapshot"
  val GSON: Gson = new GsonBuilder().setPrettyPrinting.create

  def getSnapshotPath(snapshotName: String): String = getSnapshotDirectory + "/" + snapshotName

  def getSnapshotDirectory: String = System.getProperty("LOG_LOCATION") + "/config"

  private def getSnapshotTime(filename: String) = {
    val timestamp = filename.split("-")(0)
    var d: Date = null
    try d = new SimpleDateFormat("yyyyMMddHHmmssSSS").parse(timestamp)
    catch {
      case e: ParseException =>
        e.printStackTrace() // NOPMD
    }
    d.getTime
  }
}

class FSSnapshotSerializer extends SnapshotSerializer {
  private val logger = LoggerFactory.getLogger(classOf[FSSnapshotSerializer])

  @throws[IOException]
  override def saveSnapshot(snapshot: Snapshot, prop: Properties): Unit = {
    val snapshotPath = new File(FSSnapshotSerializer.getSnapshotDirectory)
    FileUtils.forceMkdir(snapshotPath)
    val snapshotFile = new File(snapshotPath, snapshot.getName)
    if (snapshotFile.exists) {
      logger.error("Snapshot " + snapshot.getName + " already exists. Not saving the sanpshot.")
      return
    }
    val snapshotJson = FSSnapshotSerializer.GSON.toJson(snapshot, classOf[Snapshot])
    prop.put(FSSnapshotSerializer.MODEL_SNAPSHOT, snapshotJson)
    try {
      val os = Files.newOutputStream(snapshotFile.toPath)
      try {
        val osWriter = new OutputStreamWriter(os, StandardCharsets.UTF_8)
        prop.store(osWriter, "Saving snapshot")
        osWriter.flush()
        osWriter.close()
      } finally if (os != null) os.close()
    }
  }

  @throws[IOException]
  override def getSnapshot(snapshotJson: String): Snapshot = FSSnapshotSerializer.GSON.fromJson(snapshotJson, classOf[Snapshot])

  override def getLastSnapshot: Properties = {
    var latestSnapshotPath: String = null
    val configPath = Paths.get(FSSnapshotSerializer.getSnapshotDirectory)
    if (Files.exists(configPath)) try {
      val lastFilePath = Files.list(configPath).filter((f: Path) => !Files.isDirectory(f)).max(Comparator.comparingLong((f: Path) => FSSnapshotSerializer.getSnapshotTime(f.getFileName.toString)))
      if (lastFilePath.isPresent) latestSnapshotPath = lastFilePath.get.toString
    } catch {
      case e: IOException =>
        e.printStackTrace() // NOPMD
    }
    loadProperties(latestSnapshotPath)
  }

  private def loadProperties(propPath: String): Properties = {
    if (propPath != null) {
      val propFile = new File(propPath)
      try {
        val stream = Files.newInputStream(propFile.toPath)
        try {
          val prop = new Properties
          prop.load(stream)
          prop.put("tsConfigFile", propPath)
          return prop
        } catch {
          case e: IOException =>
            e.printStackTrace() // NOPMD
        } finally if (stream != null) stream.close()
      }
    }
    null
  }
}
package org.pytorch.serve.archive.utils

import com.google.gson.{Gson, GsonBuilder, JsonParseException}
import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.InvalidModelException
import org.pytorch.serve.archive.model.s3.HttpUtils
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.{LoaderOptions, Yaml}

import java.io.*
import java.net.{MalformedURLException, URL}
import java.nio.charset.StandardCharsets
import java.nio.file.{FileAlreadyExistsException, Files}
import java.util
import java.util.regex.Pattern
import scala.collection.mutable.ListBuffer

object ArchiveUtils {
  val GSON: Gson = new GsonBuilder().setPrettyPrinting.create
  private val VALID_URL_PATTERN = Pattern.compile("file?://.*|http(s)?://.*", Pattern.CASE_INSENSITIVE)

  @throws[InvalidModelException]
  @throws[IOException]
  def readFile[T](file: File, `type`: Class[T]): T = try {
    val r = new InputStreamReader(Files.newInputStream(file.toPath), StandardCharsets.UTF_8)
    try GSON.fromJson(r, `type`)
    catch {
      case e: JsonParseException =>
        throw new InvalidModelException("Failed to parse signature.json.", e)
    } finally if (r != null) r.close()
  }

  @throws[InvalidModelException]
  @throws[IOException]
  def readYamlFile[T](file: File, `type`: Class[T]): T = {
    val yaml = new Yaml(new Constructor(`type`, new LoaderOptions))
    try {
      val r = new InputStreamReader(Files.newInputStream(file.toPath), StandardCharsets.UTF_8)
      try yaml.load(r)
      catch {
        case e: YAMLException =>
          throw new InvalidModelException("Failed to parse model config yaml file.", e)
      } finally if (r != null) r.close()
    }
  }

  @throws[InvalidModelException]
  @throws[IOException]
  def readYamlFile(file: File): Map[String, AnyRef] = {
    val yaml = new Yaml
    try {
      val r = new InputStreamReader(Files.newInputStream(file.toPath), StandardCharsets.UTF_8)
      try yaml.load(r)
      catch {
        case e: YAMLException =>
          throw new InvalidModelException("Failed to parse model config yaml file.", e)
      } finally if (r != null) r.close()
    }
  }

  @throws[InvalidArchiveURLException]
  def validateURL(allowedUrls: List[String], url: String): Boolean = {
    var patternMatch = false
    import scala.jdk.CollectionConverters.*
    for (temp <- allowedUrls) {
      if (Pattern.compile(temp, Pattern.CASE_INSENSITIVE).matcher(url).matches) {
        patternMatch = true
        return patternMatch
      }
    }
    if (isValidURL(url)) {
      // case when url is valid url but does not match valid hosts
      throw new InvalidArchiveURLException("Given URL " + url + " does not match any allowed URL(s)")
    }
    patternMatch
  }

  def isValidURL(url: String): Boolean = VALID_URL_PATTERN.matcher(url).matches

  def getFilenameFromUrl(url: String): String = try {
    val archiveUrl = new URL(url)
    FilenameUtils.getName(archiveUrl.getPath)
  } catch {
    case e: MalformedURLException =>
      FilenameUtils.getName(url)
  }

  @throws[FileAlreadyExistsException]
  @throws[FileNotFoundException]
  @throws[DownloadArchiveException]
  @throws[InvalidArchiveURLException]
  def downloadArchive(allowedUrls: List[String],
                      location: File, 
                      archiveName: String, 
                      url: String, s3SseKmsEnabled: Boolean): Boolean = 
    try HttpUtils.copyURLToFile(allowedUrls, url, location, s3SseKmsEnabled, archiveName)
    catch {
      case e@(_: InvalidArchiveURLException | _: FileAlreadyExistsException) =>
        throw e
      case e: IOException =>
        FileUtils.deleteQuietly(location)
        throw new DownloadArchiveException("Failed to download archive from: " + url, e)
    }
}

//final class ArchiveUtils private {
//}
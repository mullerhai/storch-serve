package org.pytorch.serve.archive.utils

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.{FileUtils, IOUtils}

import java.io.*
import java.nio.file.Files
import java.security.{DigestInputStream, MessageDigest, NoSuchAlgorithmException}
import java.util.UUID
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

object ZipUtils {
  @throws[IOException]
  def unzip(is: InputStream, dest: File): Unit = {
    try {
      val zis = new ZipInputStream(is)
      try {
        var entry: ZipEntry = null
        while (zis.getNextEntry != null) {
          entry = zis.getNextEntry
          val file = new File(dest, entry.getName)
          val canonicalDestDir = dest.getCanonicalFile
          val canonicalFile = file.getCanonicalFile
          // Check for Zip Slip vulnerability
          if (!canonicalFile.getPath.startsWith(canonicalDestDir.getPath)) throw new IOException("Detected Zip Slip vulnerability: " + entry.getName)
          if (entry.isDirectory) FileUtils.forceMkdir(file)
          else {
            val parentFile = file.getParentFile
            FileUtils.forceMkdir(parentFile)
            try {
              val os = Files.newOutputStream(file.toPath)
              try IOUtils.copy(zis, os)
              finally if (os != null) os.close()
            }
          }
        }
      } finally if (zis != null) zis.close()
    }
  }

  @throws[IOException]
  def addToZip(prefix: Int, file: File, filter: FileFilter, zos: ZipOutputStream): Unit = {
    var name = file.getCanonicalPath.substring(prefix)
    if (name.startsWith("/")) name = name.substring(1)
    if (file.isDirectory) {
      if (!name.isEmpty) {
        val entry = new ZipEntry(name + '/')
        zos.putNextEntry(entry)
      }
      val files = file.listFiles(filter)
      if (files != null) for (f <- files) {
        addToZip(prefix, f, filter, zos)
      }
    }
    else if (file.isFile) {
      val entry = new ZipEntry(name)
      zos.putNextEntry(entry)
      try {
        val fis = Files.newInputStream(file.toPath).asInstanceOf[FileInputStream]
        try IOUtils.copy(fis, zos)
        finally if (fis != null) fis.close()
      }
    }
  }

  @throws[IOException]
  def unzip(is: InputStream, eTag: String, types: String, isMar: Boolean): File = {
    val tmpDir = FileUtils.getTempDirectory
    val modelDir = new File(tmpDir, types)
    FileUtils.forceMkdir(modelDir)
    val tmp = File.createTempFile(types, ".download")
    FileUtils.forceDelete(tmp)
    FileUtils.forceMkdir(tmp)
    var md: MessageDigest = null
    try md = MessageDigest.getInstance("SHA-256")
    catch {
      case e: NoSuchAlgorithmException =>
        throw new AssertionError(e)
    }
    if (isMar) unzip(new DigestInputStream(is, md), tmp)
    else decompressTarGzipFile(new DigestInputStream(is, md), tmp)
    val eTagTmp = if (eTag == null) UUID.randomUUID.toString.replaceAll("-", "") else  eTag 
    val dir = new File(modelDir, eTagTmp)
    FileUtils.moveDirectory(tmp, dir)
    dir
  }

  @throws[IOException]
  def decompressTarGzipFile(is: InputStream, dest: File): Unit = {
    try {
      val gzi = new GzipCompressorInputStream(is)
      val tis = new TarArchiveInputStream(gzi)
      try {
        var entry: ArchiveEntry = null
        while (tis.getNextEntry != null) {
          entry = tis.getNextEntry
          val name = entry.getName.substring(entry.getName.indexOf('/') + 1)
          val file = new File(dest, name)
          val canonicalDestDir = dest.getCanonicalFile
          val canonicalFile = file.getCanonicalFile
          // Check for Zip Slip vulnerability
          if (!canonicalFile.getPath.startsWith(canonicalDestDir.getPath)) throw new IOException("Detected Zip Slip vulnerability: " + entry.getName)
          if (entry.isDirectory) FileUtils.forceMkdir(file)
          else {
            val parentFile = file.getParentFile
            FileUtils.forceMkdir(parentFile)
            try {
              val os = Files.newOutputStream(file.toPath)
              try IOUtils.copy(tis, os)
              finally if (os != null) os.close()
            }
          }
        }
      } finally {
        if (gzi != null) gzi.close()
        if (tis != null) tis.close()
      }
    }
  }

  @throws[IOException]
  def createTempDir(eTag: String, types: String): File = {
    val tmpDir = FileUtils.getTempDirectory
    val modelDir = new File(tmpDir, types)
    val eTagTmp = if (eTag == null) UUID.randomUUID.toString.replaceAll("-", "") else eTag
//    if (eTag == null) then eTag = UUID.randomUUID.toString.replaceAll("-", "")
    val dir = new File(modelDir, eTagTmp)
    if (dir.exists) FileUtils.forceDelete(dir)
    FileUtils.forceMkdir(dir)
    dir
  }

  @throws[IOException]
  def createSymbolicDir(source: File, dest: File): File = {
    val sourceDirName = source.getName
    val targetLink = new File(dest, sourceDirName)
    Files.createSymbolicLink(targetLink.toPath, source.toPath)
    targetLink
  }
}


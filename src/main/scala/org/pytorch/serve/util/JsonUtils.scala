package org.pytorch.serve.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import scala.jdk.CollectionConverters._

object JsonUtils {
  val GSON_PRETTY: Gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").disableHtmlEscaping.setPrettyPrinting.create
  val GSON_PRETTY_EXPOSED: Gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation.disableHtmlEscaping.setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").setPrettyPrinting.create
  val GSON: Gson = new GsonBuilder().create
}

final class JsonUtils private {
}
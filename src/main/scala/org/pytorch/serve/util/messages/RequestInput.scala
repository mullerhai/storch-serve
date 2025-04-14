package org.pytorch.serve.util.messages

import java.nio.charset.StandardCharsets
import java.util
import org.pytorch.serve.util.ConfigManager
import scala.jdk.CollectionConverters._
object RequestInput {
  val TS_STREAM_NEXT = "ts_stream_next"
}

class RequestInput(private var requestId: String) {
  private var headers = new util.HashMap[String, String]
  private var parameters = new util.ArrayList[InputParameter]
  private var clientExpireTS = Long.MaxValue // default(never expire): Long.MAX_VALUE
  private var sequenceId = ""

  private var cached = false

  def getRequestId: String = requestId

  def setRequestId(requestId: String): Unit = {
    this.requestId = requestId
  }

  def getHeaders: util.HashMap[String, String] = headers

  def setHeaders(headers: util.HashMap[String, String]): Unit = {
    this.headers = headers
  }

  def updateHeaders(key: String, `val`: String): Unit = {
    headers.put(key, `val`)
    if (ConfigManager.getInstance.getTsHeaderKeySequenceId == key) setSequenceId(`val`)
  }

  def getParameters: util.ArrayList[InputParameter] = parameters

  def setParameters(parameters: util.ArrayList[InputParameter]): Unit = {
    this.parameters = parameters
  }

  def addParameter(modelInput: InputParameter): Unit = {
    parameters.add(modelInput)
  }

  def getStringParameter(key: String): String = {
//    import scala.collection.JavaConversions._
    for (param <- parameters.asScala) {
      if (key == param.getName) return new String(param.getValue, StandardCharsets.UTF_8)
    }
    null
  }

  def getClientExpireTS: Long = clientExpireTS

  def setClientExpireTS(clientTimeoutInMills: Long): Unit = {
    if (clientTimeoutInMills > 0) this.clientExpireTS = System.currentTimeMillis + clientTimeoutInMills
  }

  def getSequenceId: String = {
    if (sequenceId.isEmpty) sequenceId = headers.getOrDefault(ConfigManager.getInstance.getTsHeaderKeySequenceId, "")
    sequenceId
  }

  def setSequenceId(sequenceId: String): Unit = {
    this.sequenceId = sequenceId
  }

  def isCachedInBackend: Boolean = cached

  def setCachedInBackend(cached: Boolean): Unit = {
    this.cached = cached
  }
}
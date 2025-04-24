package org.pytorch.serve.http.api.rest

import io.netty.buffer.{ByteBuf, ByteBufOutputStream, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.http.HttpRequestHandlerChain
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.wlm.WorkerInitializationException
import org.slf4j.{Logger, LoggerFactory}

import java.io.{IOException, OutputStream, OutputStreamWriter, Writer}
import java.util
import java.util.Collections
import scala.jdk.CollectionConverters.*
object PrometheusMetricsRequestHandler {
  private val logger = LoggerFactory.getLogger(classOf[PrometheusMetricsRequestHandler])
}

class PrometheusMetricsRequestHandler

/** Creates a new {@code MetricsRequestHandler} instance. */
  extends HttpRequestHandlerChain {
  // TODO: Add plugins manager support
  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    if (segments.length >= 2 && "metrics" == segments(1)) {
      val resBuf = Unpooled.directBuffer
      val params = decoder.parameters.asScala.getOrElse("name[]", Collections.emptyList)
      var resp: FullHttpResponse = null
      try {
        val outputStream = new ByteBufOutputStream(resBuf)
        val writer = new OutputStreamWriter(outputStream)
        try {
          TextFormat.write004(writer, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(new util.HashSet[String](params)))
          resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, resBuf)
        } catch {
          case e: IOException =>
            PrometheusMetricsRequestHandler.logger.error("Exception encountered while reporting metrics")
            throw new ModelException(e.getMessage, e)
        } finally {
          if (outputStream != null) outputStream.close()
          if (writer != null) writer.close()
        }
      }
      resp.headers.set(HttpHeaderNames.CONTENT_TYPE, TextFormat.CONTENT_TYPE_004)
      NettyUtils.sendHttpResponse(ctx, resp, true)
    }
    else chain.handleRequest(ctx, req, decoder, segments)
  }
}
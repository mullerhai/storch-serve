package org.pytorch.serve.http.api.rest

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.Collections
import java.util
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.http.HttpRequestHandlerChain
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.wlm.WorkerInitializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
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
      val params = decoder.parameters.getOrDefault("name[]", Collections.emptyList)
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
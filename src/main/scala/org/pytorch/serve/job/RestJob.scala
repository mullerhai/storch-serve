package org.pytorch.serve.job

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.util.CharsetUtil
import java.util
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.archive.model.ModelVersionNotFoundException
import org.pytorch.serve.http.InternalServerException
import org.pytorch.serve.http.messages.DescribeModelResponse
import org.pytorch.serve.metrics.IMetric
import org.pytorch.serve.metrics.MetricCache
import org.pytorch.serve.util.ApiUtils
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.JsonUtils
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.util.messages.RequestInput
import org.pytorch.serve.util.messages.WorkerCommands
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters._
object RestJob {
  private val logger = LoggerFactory.getLogger(classOf[RestJob])
}

class RestJob(private var ctx: ChannelHandlerContext, modelName: String, version: String, cmd: WorkerCommands, input: RequestInput) extends Job(modelName, version, cmd, input) {
  final private var inferenceLatencyMetric = MetricCache.getInstance.getMetricFrontend("ts_inference_latency_microseconds")
  final private var queueLatencyMetric = MetricCache.getInstance.getMetricFrontend("ts_queue_latency_microseconds")
  final private var latencyMetricDimensionValues = util.Arrays.asList(getModelName, if (getModelVersion == null) "default"
  else getModelVersion, ConfigManager.getInstance.getHostName)
  final private var queueTimeMetric = MetricCache.getInstance.getMetricFrontend("QueueTime")
  final private var queueTimeMetricDimensionValues = util.Arrays.asList("Host", ConfigManager.getInstance.getHostName)
  private var responsePromise: CompletableFuture[Array[Byte]] = null
  /**
   * numStreams is used to track 4 cases -1: stream end 0: non-stream response (default use case)
   * 1: the first stream response [2, max_integer]: the 2nd and more stream response
   */
  private var numStreams = 0

  override def response(body: Array[Byte], contentType: CharSequence, statusCode: Int, statusPhrase: String, responseHeaders: util.Map[String, String]): Unit = {
    if (this.getCmd eq WorkerCommands.PREDICT) responseInference(body, contentType, statusCode, statusPhrase, responseHeaders)
    else if (this.getCmd eq WorkerCommands.DESCRIBE) responseDescribe(body, contentType, statusCode, statusPhrase, responseHeaders)
  }

  private def responseDescribe(body: Array[Byte], contentType: CharSequence, statusCode: Int, statusPhrase: String, responseHeaders: util.Map[String, String]): Unit = {
    try {
      val respList = ApiUtils.getModelDescription(this.getModelName, this.getModelVersion)
      if ((body != null && body.length != 0) && respList != null && respList.size == 1) respList.get(0).setCustomizedMetadata(body)
      val status = if (statusPhrase == null) HttpResponseStatus.valueOf(statusCode)
      else new HttpResponseStatus(statusCode, statusPhrase)
      val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, true)
      if (contentType != null && contentType.length > 0) resp.headers.set(HttpHeaderNames.CONTENT_TYPE, contentType)
      else resp.headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
      if (responseHeaders != null) {
//        import scala.collection.JavaConversions._
        for (e <- responseHeaders.entrySet.asScala) {
          resp.headers.set(e.getKey, e.getValue)
        }
      }
      val content = resp.content
      content.writeCharSequence(JsonUtils.GSON_PRETTY.toJson(respList), CharsetUtil.UTF_8)
      content.writeByte('\n')
      NettyUtils.sendHttpResponse(ctx, resp, true)
    } catch {
      case e@(_: ModelNotFoundException | _: ModelVersionNotFoundException) =>
        RestJob.logger.trace("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.NOT_FOUND, e)
    }
  }

  private def responseInference(body: Array[Byte], contentType: CharSequence, statusCode: Int, statusPhrase: String, responseHeaders: util.Map[String, String]): Unit = {
    val inferTime = System.nanoTime - getBegin
    val status = if (statusPhrase == null) HttpResponseStatus.valueOf(statusCode)
    else new HttpResponseStatus(statusCode, statusPhrase)
    var resp: HttpResponse = null
    if (responseHeaders != null && responseHeaders.containsKey(RequestInput.TS_STREAM_NEXT)) {
      resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status, false)
      numStreams = if (responseHeaders.get(RequestInput.TS_STREAM_NEXT) == "true") numStreams + 1
      else -1
    }
    else resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, true)
    if (contentType != null && contentType.length > 0) resp.headers.set(HttpHeaderNames.CONTENT_TYPE, contentType)
    if (responseHeaders != null) {
//      import scala.collection.JavaConversions._
      for (e <- responseHeaders.entrySet.asScala) {
        resp.headers.set(e.getKey, e.getValue)
      }
    }
    /*
             * We can load the models based on the configuration file.Since this Job is
             * not driven by the external connections, we could have a empty context for
             * this job. We shouldn't try to send a response to ctx if this is not triggered
             * by external clients.
             */
    if (ctx != null) if (numStreams == 0) { // non-stream response
      resp.asInstanceOf[DefaultFullHttpResponse].content.writeBytes(body)
      NettyUtils.sendHttpResponse(ctx, resp, true)
    }
    else if (numStreams == -1) { // the last response in a stream
      ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(body)))
      ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    }
    else if (numStreams == 1) { // the first response in a stream
      NettyUtils.sendHttpResponse(ctx, resp, true)
      ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(body)))
    }
    else if (numStreams > 1) { // the 2nd+ response in a stream
      ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(body)))
    }
    else if (responsePromise != null) responsePromise.complete(body)
    if (numStreams <= 0) {
      if (this.inferenceLatencyMetric != null) try this.inferenceLatencyMetric.addOrUpdate(this.latencyMetricDimensionValues, inferTime / 1000.0)
      catch {
        case e: Exception =>
          RestJob.logger.error("Failed to update frontend metric ts_inference_latency_microseconds: ", e)
      }
      if (this.queueLatencyMetric != null) try this.queueLatencyMetric.addOrUpdate(this.latencyMetricDimensionValues, (getScheduled - getBegin) / 1000.0)
      catch {
        case e: Exception =>
          RestJob.logger.error("Failed to update frontend metric ts_queue_latency_microseconds: ", e)
      }
      RestJob.logger.debug("Waiting time ns: {}, Backend time ns: {}", getScheduled - getBegin, System.nanoTime - getScheduled)
      val queueTime = TimeUnit.MILLISECONDS.convert(getScheduled - getBegin, TimeUnit.NANOSECONDS).toDouble
      if (this.queueTimeMetric != null) try this.queueTimeMetric.addOrUpdate(this.queueTimeMetricDimensionValues, queueTime)
      catch {
        case e: Exception =>
          RestJob.logger.error("Failed to update frontend metric QueueTime: ", e)
      }
    }
  }

  override def sendError(status: Int, error: String): Unit = {
    /*
             * We can load the models based on the configuration file.Since this Job is
             * not driven by the external connections, we could have a empty context for
             * this job. We shouldn't try to send a response to ctx if this is not triggered
             * by external clients.
             */
//    var st = 0
    if (ctx != null) {
      // Mapping HTTPURLConnection's HTTP_ENTITY_TOO_LARGE to Netty's INSUFFICIENT_STORAGE
      val st = if (status.equals(413)) then 507 else status
      NettyUtils.sendError(ctx, HttpResponseStatus.valueOf(st), new InternalServerException(error))
    }
    else if (responsePromise != null) responsePromise.completeExceptionally(new InternalServerException(error))
    if (this.getCmd eq WorkerCommands.PREDICT) RestJob.logger.debug("Waiting time ns: {}, Inference time ns: {}", getScheduled - getBegin, System.nanoTime - getBegin)
  }

  def getResponsePromise: CompletableFuture[Array[Byte]] = responsePromise

  def setResponsePromise(responsePromise: CompletableFuture[Array[Byte]]): Unit = {
    this.responsePromise = responsePromise
  }

  override def isOpen: Boolean = {
    val c = ctx.channel
    c.isOpen
  }
}
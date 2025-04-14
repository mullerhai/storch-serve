package org.pytorch.serve.util

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.codec.http.multipart.Attribute
import io.netty.handler.codec.http.multipart.FileUpload
import io.netty.handler.codec.http.multipart.InterfaceHttpData
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType

import io.netty.util.AttributeKey
import io.netty.util.CharsetUtil

import java.io.IOException
import java.net.SocketAddress
import java.util
import org.pytorch.serve.http.ErrorResponse
import org.pytorch.serve.http.Session
import org.pytorch.serve.http.StatusResponse
import org.pytorch.serve.metrics.IMetric
import org.pytorch.serve.metrics.MetricCache
import org.pytorch.serve.util.messages.InputParameter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import  org.pytorch.serve.util.ConfigManager

import scala.jdk.CollectionConverters.*

/** A utility class that handling Netty request and response. */
object NettyUtils {
  private val logger = LoggerFactory.getLogger("ACCESS_LOG")
  private val REQUEST_ID = "x-request-id"
  private val SESSION_KEY = AttributeKey.valueOf("session")

  def requestReceived(channel: Channel, request: HttpRequest): Unit = {
    val session = channel.attr(SESSION_KEY).get
    assert(session == null)
    val address = channel.remoteAddress
    var remoteIp: String = null
    if (address == null) {
      // This is can be null on UDS, or on certain case in Windows
      remoteIp = "127.0.0.1"
    }
    else remoteIp = address.toString
    channel.attr(SESSION_KEY).set(new Session(remoteIp, request))
  }

  def getRequestId(channel: Channel): String = {
    val accessLog:Session = channel.attr(SESSION_KEY).get.asInstanceOf[Session]
    if (accessLog != null) return accessLog.getRequestId
    null
  }

  def sendJsonResponse(ctx: ChannelHandlerContext, json: AnyRef): Unit = {
    sendJsonResponse(ctx, JsonUtils.GSON_PRETTY.toJson(json), HttpResponseStatus.OK)
  }

  def sendJsonResponse(ctx: ChannelHandlerContext, json: AnyRef, status: HttpResponseStatus): Unit = {
    sendJsonResponse(ctx, JsonUtils.GSON_PRETTY.toJson(json), status)
  }

  def sendJsonResponse(ctx: ChannelHandlerContext, statusResponse: StatusResponse): Unit = {
    sendJsonResponse(ctx, JsonUtils.GSON_PRETTY_EXPOSED.toJson(statusResponse), HttpResponseStatus.valueOf(statusResponse.getHttpResponseCode))
  }

  def sendJsonResponse(ctx: ChannelHandlerContext, json: String): Unit = {
    sendJsonResponse(ctx, json, HttpResponseStatus.OK)
  }

  def sendJsonResponse(ctx: ChannelHandlerContext, json: String, status: HttpResponseStatus): Unit = {
    val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, true)
    resp.headers.set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
    val content = resp.content
    content.writeCharSequence(json, CharsetUtil.UTF_8)
    content.writeByte('\n')
    sendHttpResponse(ctx, resp, true)
  }

  def sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus, t: Throwable): Unit = {
    val error = new ErrorResponse(status.code, t.getClass.getSimpleName, t.getMessage)
    sendJsonResponse(ctx, error, status)
  }

  def sendError(ctx: ChannelHandlerContext, status: HttpResponseStatus, t: Throwable, msg: String): Unit = {
    val error = new ErrorResponse(status.code, t.getClass.getSimpleName, msg)
    sendJsonResponse(ctx, error, status)
  }

  /**
   * Send HTTP response to client.
   *
   * @param ctx       ChannelHandlerContext
   * @param resp      HttpResponse to send
   * @param keepAlive if keep the connection
   */
  def sendHttpResponse(ctx: ChannelHandlerContext, resp: HttpResponse, keepAlive: Boolean): Unit = {
    // Send the response and close the connection if necessary.
    val channel = ctx.channel
    val session:Session = channel.attr(SESSION_KEY).getAndSet(null).asInstanceOf[Session]
    val headers = resp.headers
    val configManager = ConfigManager.getInstance
    if (session != null) {
      // session might be recycled if channel is closed already.
      session.setCode(resp.status.code)
      headers.set(REQUEST_ID, session.getRequestId)
      logger.info(session.toString)
    }
    val code = resp.status.code
    val requestsMetricDimensionValues = util.Arrays.asList("Host", ConfigManager.getInstance.getHostName)
    if (code >= 200 && code < 300) {
      val requests2xxMetric = MetricCache.getInstance.getMetricFrontend("Requests2XX")
      if (requests2xxMetric != null) try requests2xxMetric.addOrUpdate(requestsMetricDimensionValues, 1)
      catch {
        case e: Exception =>
          logger.error("Failed to update frontend metric Requests2XX: ", e)
      }
    }
    else if (code >= 400 && code < 500) {
      val requests4xxMetric = MetricCache.getInstance.getMetricFrontend("Requests4XX")
      if (requests4xxMetric != null) try requests4xxMetric.addOrUpdate(requestsMetricDimensionValues, 1)
      catch {
        case e: Exception =>
          logger.error("Failed to update frontend metric Requests4XX: ", e)
      }
    }
    else {
      val requests5xxMetric = MetricCache.getInstance.getMetricFrontend("Requests5XX")
      if (requests5xxMetric != null) try requests5xxMetric.addOrUpdate(requestsMetricDimensionValues, 1)
      catch {
        case e: Exception =>
          logger.error("Failed to update frontend metric Requests5XX: ", e)
      }
    }
    val allowedOrigin = configManager.getCorsAllowedOrigin
    val allowedMethods = configManager.getCorsAllowedMethods
    val allowedHeaders = configManager.getCorsAllowedHeaders
    if (allowedOrigin != null && !allowedOrigin.isEmpty && !headers.contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)) headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin)
    if (allowedMethods != null && !allowedMethods.isEmpty && !headers.contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS)) headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, allowedMethods)
    if (allowedHeaders != null && !allowedHeaders.isEmpty && !headers.contains(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS)) headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaders)
    // Add cache-control headers to avoid browser cache response
    headers.set("Pragma", "no-cache")
    headers.set("Cache-Control", "no-cache; no-store, must-revalidate, private")
    headers.set("Expires", "Thu, 01 Jan 1970 00:00:00 UTC")
    if (resp.isInstanceOf[FullHttpResponse]) HttpUtil.setContentLength(resp, resp.asInstanceOf[FullHttpResponse].content.readableBytes)
    else HttpUtil.setTransferEncodingChunked(resp, true)
    if (!keepAlive || code >= 400) {
      headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
      val f = channel.writeAndFlush(resp)
      f.addListener(ChannelFutureListener.CLOSE)
    }
    else {
      headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
      channel.writeAndFlush(resp)
    }
  }

  /** Closes the specified channel after all queued write requests are flushed. */
  def closeOnFlush(ch: Channel): Unit = {
    if (ch.isActive) ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
  }

  def getBytes(buf: ByteBuf): Array[Byte] = {
    if (buf.hasArray) return buf.array
    val ret = new Array[Byte](buf.readableBytes)
    val readerIndex = buf.readerIndex
    buf.getBytes(readerIndex, ret)
    ret
  }

  def getParameter(decoder: QueryStringDecoder, key: String, `def`: String): String = {
    val param = decoder.parameters.get(key)
    if (param != null && !param.isEmpty) return param.get(0)
    `def`
  }

  def getIntParameter(decoder: QueryStringDecoder, key: String, `def`: Int): Int = {
    val value = getParameter(decoder, key, null)
    if (value == null) return `def`
    try value.toInt
    catch {
      case e: NumberFormatException =>
        `def`
    }
  }

  def getFormData(data: InterfaceHttpData): InputParameter = {
    if (data == null) return null
    val name = data.getName
    data.getHttpDataType match {
      case HttpDataType.Attribute =>
        val attribute = data.asInstanceOf[Attribute]
        try new InputParameter(name, getBytes(attribute.getByteBuf))
        catch {
          case e: IOException =>
            throw new AssertionError(e)
        }
      case HttpDataType.FileUpload =>
        val fileUpload = data.asInstanceOf[FileUpload]
        val contentType = fileUpload.getContentType
        try new InputParameter(name, getBytes(fileUpload.getByteBuf), contentType)
        catch {
          case e: IOException =>
            throw new AssertionError(e)
        }
      case _ =>
        throw new IllegalArgumentException("Except form field, but got " + data.getHttpDataType)
    }
  }
}

final class NettyUtils private {
}
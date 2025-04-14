package org.pytorch.serve.http

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.model.ModelNotFoundException
import org.pytorch.serve.archive.model.ModelVersionNotFoundException
import org.pytorch.serve.archive.workflow.WorkflowNotFoundException
import org.pytorch.serve.util.NettyUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A class handling inbound HTTP requests.
 *
 * <p>This class
 */
object HttpRequestHandler {
  private val logger = LoggerFactory.getLogger(classOf[HttpRequestHandler])
}

/** Creates a new {@code HttpRequestHandler} instance. */
class HttpRequestHandler extends SimpleChannelInboundHandler[FullHttpRequest] {
  private var handlerChain: HttpRequestHandlerChain = null

  def this(chain: HttpRequestHandlerChain)= {
    this()
    handlerChain = chain
  }


  override protected def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    try {
      NettyUtils.requestReceived(ctx.channel, req)
      if (!req.decoderResult.isSuccess) throw new BadRequestException("Invalid HTTP message.")
      val decoder = new QueryStringDecoder(req.uri)
      val path = decoder.path
      val segments = path.split("/")
      handlerChain.handleRequest(ctx, req, decoder, segments)
    } catch {
      case e@(_: ResourceNotFoundException | _: ModelNotFoundException | _: ModelVersionNotFoundException | _: WorkflowNotFoundException) =>
        HttpRequestHandler.logger.trace("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.NOT_FOUND, e)
      case e@(_: BadRequestException | _: ModelException | _: DownloadArchiveException) =>
        HttpRequestHandler.logger.trace("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.BAD_REQUEST, e)
      case e: ConflictStatusException =>
        HttpRequestHandler.logger.trace("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.CONFLICT, e)
      case e: RequestTimeoutException =>
        HttpRequestHandler.logger.trace("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.REQUEST_TIMEOUT, e)
      case e: MethodNotAllowedException =>
        HttpRequestHandler.logger.trace("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED, e)
      case e: ServiceUnavailableException =>
        HttpRequestHandler.logger.trace("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.SERVICE_UNAVAILABLE, e)
      case e: OutOfMemoryError =>
        HttpRequestHandler.logger.trace("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.INSUFFICIENT_STORAGE, e)
      case e: IllegalArgumentException =>
        HttpRequestHandler.logger.error("", e)
        NettyUtils.sendError(ctx, HttpResponseStatus.FORBIDDEN, e)
      case t: Throwable =>
        HttpRequestHandler.logger.error("", t)
        NettyUtils.sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR, t)
    }
  }

  /** {@inheritDoc } */
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    HttpRequestHandler.logger.error("", cause)
    if (cause.isInstanceOf[OutOfMemoryError]) NettyUtils.sendError(ctx, HttpResponseStatus.INSUFFICIENT_STORAGE, cause)
    ctx.close
  }
}
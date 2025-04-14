package org.pytorch.serve.http

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.ssl.OptionalSslHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslHandler
import java.util
import org.pytorch.serve.util.ConfigManager
import org.pytorch.serve.util.ConnectorType
import org.pytorch.serve.util.NettyUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object ExtendedSSLHandler {
  private val logger = LoggerFactory.getLogger(classOf[ExtendedSSLHandler])
  /** the length of the ssl record header (in bytes) */
  private val SSL_RECORD_HEADER_LENGTH = 5
}

class ExtendedSSLHandler(sslContext: SslContext, var connectorType: ConnectorType) extends OptionalSslHandler(sslContext) {
  @throws[Exception]
  override protected def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    if (in.readableBytes < ExtendedSSLHandler.SSL_RECORD_HEADER_LENGTH) return
    val configMgr = ConfigManager.getInstance
    if (SslHandler.isEncrypted(in) || !configMgr.isSSLEnabled(connectorType)) super.decode(context, in, out)
    else {
      ExtendedSSLHandler.logger.error("Recieved HTTP request!")
      NettyUtils.sendJsonResponse(context, new StatusResponse("This TorchServe instance only accepts HTTPS requests", HttpResponseStatus.FORBIDDEN.code))
    }
  }
}
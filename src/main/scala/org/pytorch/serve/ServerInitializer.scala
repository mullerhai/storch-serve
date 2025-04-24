package org.pytorch.serve

import io.netty.channel.{Channel, ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.ssl.SslContext
import org.pytorch.serve.http.*
import org.pytorch.serve.http.api.rest.*
import org.pytorch.serve.servingsdk.impl.PluginsManager
import org.pytorch.serve.util.TokenAuthorization.TokenType
import org.pytorch.serve.util.{ConfigManager, ConnectorType}
import org.pytorch.serve.workflow.api.http.{WorkflowInferenceRequestHandler, WorkflowMgmtRequestHandler}
import org.slf4j.{Logger, LoggerFactory}

/**
 * A special {@link io.netty.channel.ChannelInboundHandler} which offers an easy way to initialize a
 * {@link io.netty.channel.Channel} once it was registered to its {@link
 * io.netty.channel.EventLoop}.
 */
object ServerInitializer {
  private val logger = LoggerFactory.getLogger(classOf[ServerInitializer])
}

/**
 * Creates a new {@code HttpRequestHandler} instance.
 *
 * @param sslCtx null if SSL is not enabled
 * @param types  true to initialize a management server instead of an API Server
 */
class ServerInitializer(private var sslCtx: SslContext, private var connectorType: ConnectorType) extends ChannelInitializer[Channel] {
  /** {@inheritDoc } */
  override def initChannel(ch: Channel): Unit = {
    val pipeline = ch.pipeline
    val apiDescriptionRequestHandler = new ApiDescriptionRequestHandler(connectorType)
    val invalidRequestHandler = new InvalidRequestHandler
    val maxRequestSize = ConfigManager.getInstance.getMaxRequestSize
    if (sslCtx != null) pipeline.addLast("ssl", new ExtendedSSLHandler(sslCtx, connectorType))
    pipeline.addLast("http", new HttpServerCodec)
    pipeline.addLast("aggregator", new HttpObjectAggregator(maxRequestSize))
    var httpRequestHandlerChain = apiDescriptionRequestHandler.asInstanceOf[HttpRequestHandlerChain]
    if (ConnectorType.ALL == connectorType || ConnectorType.INFERENCE_CONNECTOR == connectorType) {
      httpRequestHandlerChain = httpRequestHandlerChain.setNextHandler(new TokenAuthorizationHandler(TokenType.INFERENCE))
      httpRequestHandlerChain = httpRequestHandlerChain.setNextHandler(new InferenceRequestHandler(PluginsManager.getInstance.getInferenceEndpoints))
      httpRequestHandlerChain = httpRequestHandlerChain.setNextHandler(new WorkflowInferenceRequestHandler)
      // Added OIP protocol with inference connector
      if (ConfigManager.getInstance.isOpenInferenceProtocol) {
        ServerInitializer.logger.info("OIP added with handler chain")
        httpRequestHandlerChain = httpRequestHandlerChain.setNextHandler(new OpenInferenceProtocolRequestHandler)
      }
    }
    if (ConnectorType.ALL == connectorType || ConnectorType.MANAGEMENT_CONNECTOR == connectorType) {
      httpRequestHandlerChain = httpRequestHandlerChain.setNextHandler(new TokenAuthorizationHandler(TokenType.MANAGEMENT))
      httpRequestHandlerChain = httpRequestHandlerChain.setNextHandler(new ManagementRequestHandler(PluginsManager.getInstance.getManagementEndpoints))
      httpRequestHandlerChain = httpRequestHandlerChain.setNextHandler(new WorkflowMgmtRequestHandler)
    }
    if (ConfigManager.getInstance.isMetricApiEnable && ConnectorType.ALL == connectorType || ConnectorType.METRICS_CONNECTOR == connectorType) httpRequestHandlerChain = httpRequestHandlerChain.setNextHandler(new PrometheusMetricsRequestHandler)
    httpRequestHandlerChain.setNextHandler(invalidRequestHandler)
    pipeline.addLast("handler", new HttpRequestHandler(apiDescriptionRequestHandler))
  }
}
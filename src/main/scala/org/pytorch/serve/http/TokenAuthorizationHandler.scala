package org.pytorch.serve.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.QueryStringDecoder
import java.util
import org.pytorch.serve.archive.DownloadArchiveException
import org.pytorch.serve.archive.model.InvalidKeyException
import org.pytorch.serve.archive.model.ModelException
import org.pytorch.serve.archive.workflow.WorkflowException
import org.pytorch.serve.util.NettyUtils
import org.pytorch.serve.util.TokenAuthorization
import org.pytorch.serve.util.TokenAuthorization.TokenType
import org.pytorch.serve.wlm.WorkerInitializationException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A class handling token check for all inbound HTTP requests
 *
 * <p>This class //
 */
object TokenAuthorizationHandler {
  private val logger = LoggerFactory.getLogger(classOf[TokenAuthorizationHandler])
}

class TokenAuthorizationHandler( var tokenType: TokenAuthorization.TokenType)

/** Creates a new {@code InferenceRequestHandler} instance. */
  extends HttpRequestHandlerChain {
  @throws[ModelException]
  @throws[DownloadArchiveException]
  @throws[WorkflowException]
  @throws[WorkerInitializationException]
  override def handleRequest(ctx: ChannelHandlerContext, req: FullHttpRequest, decoder: QueryStringDecoder, segments: Array[String]): Unit = {
    if (TokenAuthorization.isEnabled) if (tokenType eq TokenType.MANAGEMENT) if (req.toString.contains("/token")) try {
      checkTokenAuthorization(req, TokenType.TOKEN_API)
      val queryResponse = parseQuery(req)
      val resp = TokenAuthorization.updateKeyFile(TokenType.valueOf(queryResponse.toUpperCase))
      NettyUtils.sendJsonResponse(ctx, resp)
      return
    } catch {
      case e: Exception =>
        TokenAuthorizationHandler.logger.error("Failed to update key file")
        throw new InvalidKeyException("Token Authentication failed. Token either incorrect, expired, or not provided correctly")
    }
    else checkTokenAuthorization(req, TokenType.MANAGEMENT)
    else if (tokenType eq TokenType.INFERENCE) checkTokenAuthorization(req, TokenType.INFERENCE)
    chain.handleRequest(ctx, req, decoder, segments)
  }

  @throws[ModelException]
  private def checkTokenAuthorization(req: FullHttpRequest, tokenType: TokenAuthorization.TokenType): Unit = {
    val tokenBearer = req.headers.get("Authorization")
    if (tokenBearer == null) throw new InvalidKeyException("Token Authorization failed. Token either incorrect, expired, or not provided correctly")
    val token = TokenAuthorization.parseTokenFromBearerTokenHeader(tokenBearer)
    if (!TokenAuthorization.checkTokenAuthorization(token, tokenType)) throw new InvalidKeyException("Token Authorization failed. Token either incorrect, expired, or not provided correctly")
  }

  // parses query and either returns management/inference or a wrong type error
  private def parseQuery(req: FullHttpRequest): String = {
    val decoder = new QueryStringDecoder(req.uri)
    val parameters = decoder.parameters
    val values = parameters.get("type")
    if (values != null && !values.isEmpty) if ("management" == values.get(0) || "inference" == values.get(0)) return values.get(0)
    else return "WRONG TYPE"
    "NO TYPE PROVIDED"
  }
}
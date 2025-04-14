package org.pytorch.serve.grpcimpl

import io.grpc.ForwardingServerCall
import io.grpc.Grpc
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import org.pytorch.serve.http.Session
import org.pytorch.serve.util.ConnectorType
import org.pytorch.serve.util.ConnectorType.{INFERENCE_CONNECTOR, MANAGEMENT_CONNECTOR}
import org.pytorch.serve.util.TokenAuthorization
import org.pytorch.serve.util.TokenAuthorization.TokenType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object GRPCInterceptor {
  private val tokenAuthHeaderKey = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
  private val logger = LoggerFactory.getLogger("ACCESS_LOG")
}

class GRPCInterceptor(connectorType: ConnectorType) extends ServerInterceptor {
  private var tokenType: TokenAuthorization.TokenType = null

  connectorType match {
    case MANAGEMENT_CONNECTOR =>
      tokenType = TokenType.MANAGEMENT
    case INFERENCE_CONNECTOR =>
      tokenType = TokenType.INFERENCE
    case _ =>
      tokenType = TokenType.INFERENCE
  }
 
  override def interceptCall[ReqT, RespT](call: ServerCall[ReqT, RespT], headers: Metadata, next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
    val inetSocketString = call.getAttributes.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString
    val serviceName = call.getMethodDescriptor.getFullMethodName
    val session = new Session(inetSocketString, serviceName)
    if (TokenAuthorization.isEnabled) if (!headers.containsKey(GRPCInterceptor.tokenAuthHeaderKey) || !checkTokenAuthorization(headers.get(GRPCInterceptor.tokenAuthHeaderKey))) {
      call.close(Status.PERMISSION_DENIED.withDescription("Token Authorization failed. Token either incorrect, expired, or not provided correctly"), new Metadata)
      return new ServerCall.Listener[ReqT]() {}
    }
    next.startCall(new ForwardingServerCall.SimpleForwardingServerCall[ReqT, RespT](call) {
      override def close(status: Status, trailers: Metadata): Unit = {
        session.setCode(status.getCode.value)
        GRPCInterceptor.logger.info(session.toString)
        super.close(status, trailers)
      }
    }, headers)
  }

  private def checkTokenAuthorization(tokenAuthHeaderValue: String): Boolean = {
    if (tokenAuthHeaderValue == null) return false
    val token = TokenAuthorization.parseTokenFromBearerTokenHeader(tokenAuthHeaderValue)
    TokenAuthorization.checkTokenAuthorization(token, tokenType)
  }
}
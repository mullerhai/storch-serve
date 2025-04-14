package org.pytorch.serve.grpcimpl

import io.grpc.BindableService
import org.pytorch.serve.util.ConnectorType


object GRPCServiceFactory {
  def getgRPCService(connectorType: ConnectorType): BindableService = {
    var torchServeService: BindableService = null
    connectorType match {
      case ConnectorType.MANAGEMENT_CONNECTOR =>
        torchServeService = new ManagementImpl
      case ConnectorType.INFERENCE_CONNECTOR =>
        torchServeService = new InferenceImpl
      case ConnectorType.OPEN_INFERENCE_CONNECTOR =>
        torchServeService = new OpenInferenceProtocolImpl
      case _ =>
    }
    torchServeService
  }
}

final class GRPCServiceFactory private {
}
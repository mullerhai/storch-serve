// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!

package org.pytorch.serve.grpc.inference.inference

object InferenceAPIsServiceGrpc {
  val METHOD_PING: _root_.io.grpc.MethodDescriptor[com.google.protobuf.empty.Empty, org.pytorch.serve.grpc.inference.inference.TorchServeHealthResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.inference.InferenceAPIsService", "Ping"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[com.google.protobuf.empty.Empty])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.inference.inference.TorchServeHealthResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.inference.inference.InferenceProto.javaDescriptor.getServices().get(0).getMethods().get(0)))
      .build()
  
  val METHOD_PREDICTIONS: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.inference.inference.PredictionsRequest, org.pytorch.serve.grpc.inference.inference.PredictionResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.inference.InferenceAPIsService", "Predictions"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.inference.inference.PredictionsRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.inference.inference.PredictionResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.inference.inference.InferenceProto.javaDescriptor.getServices().get(0).getMethods().get(1)))
      .build()
  
  val METHOD_STREAM_PREDICTIONS: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.inference.inference.PredictionsRequest, org.pytorch.serve.grpc.inference.inference.PredictionResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.inference.InferenceAPIsService", "StreamPredictions"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.inference.inference.PredictionsRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.inference.inference.PredictionResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.inference.inference.InferenceProto.javaDescriptor.getServices().get(0).getMethods().get(2)))
      .build()
  
  val METHOD_STREAM_PREDICTIONS2: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.inference.inference.PredictionsRequest, org.pytorch.serve.grpc.inference.inference.PredictionResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.inference.InferenceAPIsService", "StreamPredictions2"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.inference.inference.PredictionsRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.inference.inference.PredictionResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.inference.inference.InferenceProto.javaDescriptor.getServices().get(0).getMethods().get(3)))
      .build()
  
  val SERVICE: _root_.io.grpc.ServiceDescriptor =
    _root_.io.grpc.ServiceDescriptor.newBuilder("org.pytorch.serve.grpc.inference.InferenceAPIsService")
      .setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(org.pytorch.serve.grpc.inference.inference.InferenceProto.javaDescriptor))
      .addMethod(METHOD_PING)
      .addMethod(METHOD_PREDICTIONS)
      .addMethod(METHOD_STREAM_PREDICTIONS)
      .addMethod(METHOD_STREAM_PREDICTIONS2)
      .build()
  
  trait InferenceAPIsService extends _root_.scalapb.grpc.AbstractService {
    override def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[InferenceAPIsService] = InferenceAPIsService
    /** Check health status of the TorchServe server.
      */
    def ping(request: com.google.protobuf.empty.Empty): scala.concurrent.Future[org.pytorch.serve.grpc.inference.inference.TorchServeHealthResponse]
    /** Predictions entry point to get inference using default model version.
      */
    def predictions(request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest): scala.concurrent.Future[org.pytorch.serve.grpc.inference.inference.PredictionResponse]
    /** Streaming response for an inference request.
      */
    def streamPredictions(request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest, responseObserver: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionResponse]): _root_.scala.Unit
    /** Bi-direction streaming inference and response.
      */
    def streamPredictions2(responseObserver: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionResponse]): _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionsRequest]
  }
  
  object InferenceAPIsService extends _root_.scalapb.grpc.ServiceCompanion[InferenceAPIsService] {
    implicit def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[InferenceAPIsService] = this
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = org.pytorch.serve.grpc.inference.inference.InferenceProto.javaDescriptor.getServices().get(0)
    def scalaDescriptor: _root_.scalapb.descriptors.ServiceDescriptor = org.pytorch.serve.grpc.inference.inference.InferenceProto.scalaDescriptor.services(0)
    def bindService(serviceImpl: InferenceAPIsService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition =
      _root_.io.grpc.ServerServiceDefinition.builder(SERVICE)
      .addMethod(
        METHOD_PING,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall((request: com.google.protobuf.empty.Empty, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.TorchServeHealthResponse]) => {
          serviceImpl.ping(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
        }))
      .addMethod(
        METHOD_PREDICTIONS,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall((request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionResponse]) => {
          serviceImpl.predictions(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
        }))
      .addMethod(
        METHOD_STREAM_PREDICTIONS,
        _root_.io.grpc.stub.ServerCalls.asyncServerStreamingCall((request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionResponse]) => {
          serviceImpl.streamPredictions(request, observer)
        }))
      .addMethod(
        METHOD_STREAM_PREDICTIONS2,
        _root_.io.grpc.stub.ServerCalls.asyncBidiStreamingCall((observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionResponse]) => {
          serviceImpl.streamPredictions2(observer)
        }))
      .build()
  }
  
  trait InferenceAPIsServiceBlockingClient {
    def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[InferenceAPIsService] = InferenceAPIsService
    /** Check health status of the TorchServe server.
      */
    def ping(request: com.google.protobuf.empty.Empty): org.pytorch.serve.grpc.inference.inference.TorchServeHealthResponse
    /** Predictions entry point to get inference using default model version.
      */
    def predictions(request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest): org.pytorch.serve.grpc.inference.inference.PredictionResponse
    /** Streaming response for an inference request.
      */
    def streamPredictions(request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest): scala.collection.Iterator[org.pytorch.serve.grpc.inference.inference.PredictionResponse]
  }
  
  class InferenceAPIsServiceBlockingStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[InferenceAPIsServiceBlockingStub](channel, options) with InferenceAPIsServiceBlockingClient {
    /** Check health status of the TorchServe server.
      */
    override def ping(request: com.google.protobuf.empty.Empty): org.pytorch.serve.grpc.inference.inference.TorchServeHealthResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_PING, options, request)
    }
    
    /** Predictions entry point to get inference using default model version.
      */
    override def predictions(request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest): org.pytorch.serve.grpc.inference.inference.PredictionResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_PREDICTIONS, options, request)
    }
    
    /** Streaming response for an inference request.
      */
    override def streamPredictions(request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest): scala.collection.Iterator[org.pytorch.serve.grpc.inference.inference.PredictionResponse] = {
      _root_.scalapb.grpc.ClientCalls.blockingServerStreamingCall(channel, METHOD_STREAM_PREDICTIONS, options, request)
    }
    
    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): InferenceAPIsServiceBlockingStub = new InferenceAPIsServiceBlockingStub(channel, options)
  }
  
  class InferenceAPIsServiceStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[InferenceAPIsServiceStub](channel, options) with InferenceAPIsService {
    /** Check health status of the TorchServe server.
      */
    override def ping(request: com.google.protobuf.empty.Empty): scala.concurrent.Future[org.pytorch.serve.grpc.inference.inference.TorchServeHealthResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_PING, options, request)
    }
    
    /** Predictions entry point to get inference using default model version.
      */
    override def predictions(request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest): scala.concurrent.Future[org.pytorch.serve.grpc.inference.inference.PredictionResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_PREDICTIONS, options, request)
    }
    
    /** Streaming response for an inference request.
      */
    override def streamPredictions(request: org.pytorch.serve.grpc.inference.inference.PredictionsRequest, responseObserver: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionResponse]): _root_.scala.Unit = {
      _root_.scalapb.grpc.ClientCalls.asyncServerStreamingCall(channel, METHOD_STREAM_PREDICTIONS, options, request, responseObserver)
    }
    
    /** Bi-direction streaming inference and response.
      */
    override def streamPredictions2(responseObserver: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionResponse]): _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.inference.inference.PredictionsRequest] = {
      _root_.scalapb.grpc.ClientCalls.asyncBidiStreamingCall(channel, METHOD_STREAM_PREDICTIONS2, options, responseObserver)
    }
    
    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): InferenceAPIsServiceStub = new InferenceAPIsServiceStub(channel, options)
  }
  
  object InferenceAPIsServiceStub extends _root_.io.grpc.stub.AbstractStub.StubFactory[InferenceAPIsServiceStub] {
    override def newStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): InferenceAPIsServiceStub = new InferenceAPIsServiceStub(channel, options)
    
    implicit val stubFactory: _root_.io.grpc.stub.AbstractStub.StubFactory[InferenceAPIsServiceStub] = this
  }
  
  def bindService(serviceImpl: InferenceAPIsService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition = InferenceAPIsService.bindService(serviceImpl, executionContext)
  
  def blockingStub(channel: _root_.io.grpc.Channel): InferenceAPIsServiceBlockingStub = new InferenceAPIsServiceBlockingStub(channel)
  
  def stub(channel: _root_.io.grpc.Channel): InferenceAPIsServiceStub = new InferenceAPIsServiceStub(channel)
  
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = org.pytorch.serve.grpc.inference.inference.InferenceProto.javaDescriptor.getServices().get(0)
  
}
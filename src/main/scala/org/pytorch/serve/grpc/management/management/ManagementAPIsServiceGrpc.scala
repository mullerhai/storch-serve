// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!

package org.pytorch.serve.grpc.management.management

object ManagementAPIsServiceGrpc {
  val METHOD_DESCRIBE_MODEL: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.management.management.DescribeModelRequest, org.pytorch.serve.grpc.management.management.ManagementResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.management.ManagementAPIsService", "DescribeModel"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.DescribeModelRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.ManagementResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getServices().get(0).getMethods().get(0)))
      .build()
  
  val METHOD_LIST_MODELS: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.management.management.ListModelsRequest, org.pytorch.serve.grpc.management.management.ManagementResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.management.ManagementAPIsService", "ListModels"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.ListModelsRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.ManagementResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getServices().get(0).getMethods().get(1)))
      .build()
  
  val METHOD_REGISTER_MODEL: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.management.management.RegisterModelRequest, org.pytorch.serve.grpc.management.management.ManagementResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.management.ManagementAPIsService", "RegisterModel"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.RegisterModelRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.ManagementResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getServices().get(0).getMethods().get(2)))
      .build()
  
  val METHOD_SCALE_WORKER: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.management.management.ScaleWorkerRequest, org.pytorch.serve.grpc.management.management.ManagementResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.management.ManagementAPIsService", "ScaleWorker"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.ScaleWorkerRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.ManagementResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getServices().get(0).getMethods().get(3)))
      .build()
  
  val METHOD_SET_DEFAULT: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.management.management.SetDefaultRequest, org.pytorch.serve.grpc.management.management.ManagementResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.management.ManagementAPIsService", "SetDefault"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.SetDefaultRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.ManagementResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getServices().get(0).getMethods().get(4)))
      .build()
  
  val METHOD_UNREGISTER_MODEL: _root_.io.grpc.MethodDescriptor[org.pytorch.serve.grpc.management.management.UnregisterModelRequest, org.pytorch.serve.grpc.management.management.ManagementResponse] =
    _root_.io.grpc.MethodDescriptor.newBuilder()
      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("org.pytorch.serve.grpc.management.ManagementAPIsService", "UnregisterModel"))
      .setSampledToLocalTracing(true)
      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.UnregisterModelRequest])
      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[org.pytorch.serve.grpc.management.management.ManagementResponse])
      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getServices().get(0).getMethods().get(5)))
      .build()
  
  val SERVICE: _root_.io.grpc.ServiceDescriptor =
    _root_.io.grpc.ServiceDescriptor.newBuilder("org.pytorch.serve.grpc.management.ManagementAPIsService")
      .setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor))
      .addMethod(METHOD_DESCRIBE_MODEL)
      .addMethod(METHOD_LIST_MODELS)
      .addMethod(METHOD_REGISTER_MODEL)
      .addMethod(METHOD_SCALE_WORKER)
      .addMethod(METHOD_SET_DEFAULT)
      .addMethod(METHOD_UNREGISTER_MODEL)
      .build()
  
  trait ManagementAPIsService extends _root_.scalapb.grpc.AbstractService {
    override def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[ManagementAPIsService] = ManagementAPIsService
    /** Provides detailed information about the default version of a model.
      */
    def describeModel(request: org.pytorch.serve.grpc.management.management.DescribeModelRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse]
    /** List registered models in TorchServe.
      */
    def listModels(request: org.pytorch.serve.grpc.management.management.ListModelsRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse]
    /** Register a new model in TorchServe.
      */
    def registerModel(request: org.pytorch.serve.grpc.management.management.RegisterModelRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse]
    /** Configure number of workers for a default version of a model. This is an asynchronous call by default. Caller need to call describeModel to check if the model workers has been changed.
      */
    def scaleWorker(request: org.pytorch.serve.grpc.management.management.ScaleWorkerRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse]
    /** Set default version of a model
      */
    def setDefault(request: org.pytorch.serve.grpc.management.management.SetDefaultRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse]
    /** Unregister the default version of a model from TorchServe if it is the only version available. This is an asynchronous call by default. Caller can call listModels to confirm model is unregistered.
      */
    def unregisterModel(request: org.pytorch.serve.grpc.management.management.UnregisterModelRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse]
  }
  
  object ManagementAPIsService extends _root_.scalapb.grpc.ServiceCompanion[ManagementAPIsService] {
    implicit def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[ManagementAPIsService] = this
    def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getServices().get(0)
    def scalaDescriptor: _root_.scalapb.descriptors.ServiceDescriptor = org.pytorch.serve.grpc.management.management.ManagementProto.scalaDescriptor.services(0)
    def bindService(serviceImpl: ManagementAPIsService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition =
      _root_.io.grpc.ServerServiceDefinition.builder(SERVICE)
      .addMethod(
        METHOD_DESCRIBE_MODEL,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall((request: org.pytorch.serve.grpc.management.management.DescribeModelRequest, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.management.management.ManagementResponse]) => {
          serviceImpl.describeModel(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
        }))
      .addMethod(
        METHOD_LIST_MODELS,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall((request: org.pytorch.serve.grpc.management.management.ListModelsRequest, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.management.management.ManagementResponse]) => {
          serviceImpl.listModels(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
        }))
      .addMethod(
        METHOD_REGISTER_MODEL,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall((request: org.pytorch.serve.grpc.management.management.RegisterModelRequest, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.management.management.ManagementResponse]) => {
          serviceImpl.registerModel(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
        }))
      .addMethod(
        METHOD_SCALE_WORKER,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall((request: org.pytorch.serve.grpc.management.management.ScaleWorkerRequest, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.management.management.ManagementResponse]) => {
          serviceImpl.scaleWorker(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
        }))
      .addMethod(
        METHOD_SET_DEFAULT,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall((request: org.pytorch.serve.grpc.management.management.SetDefaultRequest, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.management.management.ManagementResponse]) => {
          serviceImpl.setDefault(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
        }))
      .addMethod(
        METHOD_UNREGISTER_MODEL,
        _root_.io.grpc.stub.ServerCalls.asyncUnaryCall((request: org.pytorch.serve.grpc.management.management.UnregisterModelRequest, observer: _root_.io.grpc.stub.StreamObserver[org.pytorch.serve.grpc.management.management.ManagementResponse]) => {
          serviceImpl.unregisterModel(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
            executionContext)
        }))
      .build()
  }
  
  trait ManagementAPIsServiceBlockingClient {
    def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[ManagementAPIsService] = ManagementAPIsService
    /** Provides detailed information about the default version of a model.
      */
    def describeModel(request: org.pytorch.serve.grpc.management.management.DescribeModelRequest): org.pytorch.serve.grpc.management.management.ManagementResponse
    /** List registered models in TorchServe.
      */
    def listModels(request: org.pytorch.serve.grpc.management.management.ListModelsRequest): org.pytorch.serve.grpc.management.management.ManagementResponse
    /** Register a new model in TorchServe.
      */
    def registerModel(request: org.pytorch.serve.grpc.management.management.RegisterModelRequest): org.pytorch.serve.grpc.management.management.ManagementResponse
    /** Configure number of workers for a default version of a model. This is an asynchronous call by default. Caller need to call describeModel to check if the model workers has been changed.
      */
    def scaleWorker(request: org.pytorch.serve.grpc.management.management.ScaleWorkerRequest): org.pytorch.serve.grpc.management.management.ManagementResponse
    /** Set default version of a model
      */
    def setDefault(request: org.pytorch.serve.grpc.management.management.SetDefaultRequest): org.pytorch.serve.grpc.management.management.ManagementResponse
    /** Unregister the default version of a model from TorchServe if it is the only version available. This is an asynchronous call by default. Caller can call listModels to confirm model is unregistered.
      */
    def unregisterModel(request: org.pytorch.serve.grpc.management.management.UnregisterModelRequest): org.pytorch.serve.grpc.management.management.ManagementResponse
  }
  
  class ManagementAPIsServiceBlockingStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[ManagementAPIsServiceBlockingStub](channel, options) with ManagementAPIsServiceBlockingClient {
    /** Provides detailed information about the default version of a model.
      */
    override def describeModel(request: org.pytorch.serve.grpc.management.management.DescribeModelRequest): org.pytorch.serve.grpc.management.management.ManagementResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_DESCRIBE_MODEL, options, request)
    }
    
    /** List registered models in TorchServe.
      */
    override def listModels(request: org.pytorch.serve.grpc.management.management.ListModelsRequest): org.pytorch.serve.grpc.management.management.ManagementResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_LIST_MODELS, options, request)
    }
    
    /** Register a new model in TorchServe.
      */
    override def registerModel(request: org.pytorch.serve.grpc.management.management.RegisterModelRequest): org.pytorch.serve.grpc.management.management.ManagementResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_REGISTER_MODEL, options, request)
    }
    
    /** Configure number of workers for a default version of a model. This is an asynchronous call by default. Caller need to call describeModel to check if the model workers has been changed.
      */
    override def scaleWorker(request: org.pytorch.serve.grpc.management.management.ScaleWorkerRequest): org.pytorch.serve.grpc.management.management.ManagementResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_SCALE_WORKER, options, request)
    }
    
    /** Set default version of a model
      */
    override def setDefault(request: org.pytorch.serve.grpc.management.management.SetDefaultRequest): org.pytorch.serve.grpc.management.management.ManagementResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_SET_DEFAULT, options, request)
    }
    
    /** Unregister the default version of a model from TorchServe if it is the only version available. This is an asynchronous call by default. Caller can call listModels to confirm model is unregistered.
      */
    override def unregisterModel(request: org.pytorch.serve.grpc.management.management.UnregisterModelRequest): org.pytorch.serve.grpc.management.management.ManagementResponse = {
      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_UNREGISTER_MODEL, options, request)
    }
    
    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): ManagementAPIsServiceBlockingStub = new ManagementAPIsServiceBlockingStub(channel, options)
  }
  
  class ManagementAPIsServiceStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[ManagementAPIsServiceStub](channel, options) with ManagementAPIsService {
    /** Provides detailed information about the default version of a model.
      */
    override def describeModel(request: org.pytorch.serve.grpc.management.management.DescribeModelRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_DESCRIBE_MODEL, options, request)
    }
    
    /** List registered models in TorchServe.
      */
    override def listModels(request: org.pytorch.serve.grpc.management.management.ListModelsRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_LIST_MODELS, options, request)
    }
    
    /** Register a new model in TorchServe.
      */
    override def registerModel(request: org.pytorch.serve.grpc.management.management.RegisterModelRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_REGISTER_MODEL, options, request)
    }
    
    /** Configure number of workers for a default version of a model. This is an asynchronous call by default. Caller need to call describeModel to check if the model workers has been changed.
      */
    override def scaleWorker(request: org.pytorch.serve.grpc.management.management.ScaleWorkerRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_SCALE_WORKER, options, request)
    }
    
    /** Set default version of a model
      */
    override def setDefault(request: org.pytorch.serve.grpc.management.management.SetDefaultRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_SET_DEFAULT, options, request)
    }
    
    /** Unregister the default version of a model from TorchServe if it is the only version available. This is an asynchronous call by default. Caller can call listModels to confirm model is unregistered.
      */
    override def unregisterModel(request: org.pytorch.serve.grpc.management.management.UnregisterModelRequest): scala.concurrent.Future[org.pytorch.serve.grpc.management.management.ManagementResponse] = {
      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_UNREGISTER_MODEL, options, request)
    }
    
    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): ManagementAPIsServiceStub = new ManagementAPIsServiceStub(channel, options)
  }
  
  object ManagementAPIsServiceStub extends _root_.io.grpc.stub.AbstractStub.StubFactory[ManagementAPIsServiceStub] {
    override def newStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): ManagementAPIsServiceStub = new ManagementAPIsServiceStub(channel, options)
    
    implicit val stubFactory: _root_.io.grpc.stub.AbstractStub.StubFactory[ManagementAPIsServiceStub] = this
  }
  
  def bindService(serviceImpl: ManagementAPIsService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition = ManagementAPIsService.bindService(serviceImpl, executionContext)
  
  def blockingStub(channel: _root_.io.grpc.Channel): ManagementAPIsServiceBlockingStub = new ManagementAPIsServiceBlockingStub(channel)
  
  def stub(channel: _root_.io.grpc.Channel): ManagementAPIsServiceStub = new ManagementAPIsServiceStub(channel)
  
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getServices().get(0)
  
}
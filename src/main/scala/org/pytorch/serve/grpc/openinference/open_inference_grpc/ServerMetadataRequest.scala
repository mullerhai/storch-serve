// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!

package org.pytorch.serve.grpc.openinference.open_inference_grpc

@SerialVersionUID(0L)
final case class ServerMetadataRequest(
    unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[ServerMetadataRequest] {
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      __size += unknownFields.serializedSize
      __size
    }
    override def serializedSize: _root_.scala.Int = {
      var __size = __serializedSizeMemoized
      if (__size == 0) {
        __size = __computeSerializedSize() + 1
        __serializedSizeMemoized = __size
      }
      __size - 1
      
    }
    def writeTo(`_output__`: _root_.com.google.protobuf.CodedOutputStream): _root_.scala.Unit = {
      unknownFields.writeTo(_output__)
    }
    def withUnknownFields(__v: _root_.scalapb.UnknownFieldSet) = copy(unknownFields = __v)
    def discardUnknownFields = copy(unknownFields = _root_.scalapb.UnknownFieldSet.empty)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = throw new MatchError(__fieldNumber)
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = throw new MatchError(__field)
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest.type = org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest
    // @@protoc_insertion_point(GeneratedMessage[org.pytorch.serve.grpc.openinference.ServerMetadataRequest])
}

object ServerMetadataRequest extends scalapb.GeneratedMessageCompanion[org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest = {
    var `_unknownFields__`: _root_.scalapb.UnknownFieldSet.Builder = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case tag =>
          if (_unknownFields__ == null) {
            _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
          }
          _unknownFields__.parseField(tag, _input__)
      }
    }
    org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest(
        unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest(
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpcProto.javaDescriptor.getMessageTypes().get(6)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = org.pytorch.serve.grpc.openinference.open_inference_grpc.OpenInferenceGrpcProto.scalaDescriptor.messages(6)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[?] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[? <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[?] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest(
  )
  implicit class ServerMetadataRequestLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest](_l) {
  }
  def of(
  ): _root_.org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest = _root_.org.pytorch.serve.grpc.openinference.open_inference_grpc.ServerMetadataRequest(
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[org.pytorch.serve.grpc.openinference.ServerMetadataRequest])
}

// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!

package org.pytorch.serve.grpc.management.management

/** @param msg
  *   Response string of different management API calls.
  */
@SerialVersionUID(0L)
final case class ManagementResponse(
    msg: _root_.scala.Predef.String = "",
    unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[ManagementResponse] {
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = msg
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(1, __value)
        }
      };
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
      {
        val __v = msg
        if (!__v.isEmpty) {
          _output__.writeString(1, __v)
        }
      };
      unknownFields.writeTo(_output__)
    }
    def withMsg(__v: _root_.scala.Predef.String): ManagementResponse = copy(msg = __v)
    def withUnknownFields(__v: _root_.scalapb.UnknownFieldSet) = copy(unknownFields = __v)
    def discardUnknownFields = copy(unknownFields = _root_.scalapb.UnknownFieldSet.empty)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = msg
          if (__t != "") __t else null
        }
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PString(msg)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: org.pytorch.serve.grpc.management.management.ManagementResponse.type = org.pytorch.serve.grpc.management.management.ManagementResponse
    // @@protoc_insertion_point(GeneratedMessage[org.pytorch.serve.grpc.management.ManagementResponse])
}

object ManagementResponse extends scalapb.GeneratedMessageCompanion[org.pytorch.serve.grpc.management.management.ManagementResponse] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[org.pytorch.serve.grpc.management.management.ManagementResponse] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): org.pytorch.serve.grpc.management.management.ManagementResponse = {
    var __msg: _root_.scala.Predef.String = ""
    var `_unknownFields__`: _root_.scalapb.UnknownFieldSet.Builder = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 10 =>
          __msg = _input__.readStringRequireUtf8()
        case tag =>
          if (_unknownFields__ == null) {
            _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
          }
          _unknownFields__.parseField(tag, _input__)
      }
    }
    org.pytorch.serve.grpc.management.management.ManagementResponse(
        msg = __msg,
        unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[org.pytorch.serve.grpc.management.management.ManagementResponse] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      org.pytorch.serve.grpc.management.management.ManagementResponse(
        msg = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Predef.String]).getOrElse("")
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = org.pytorch.serve.grpc.management.management.ManagementProto.javaDescriptor.getMessageTypes().get(0)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = org.pytorch.serve.grpc.management.management.ManagementProto.scalaDescriptor.messages(0)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[?] = throw new MatchError(__number)
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[? <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[?] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = org.pytorch.serve.grpc.management.management.ManagementResponse(
    msg = ""
  )
  implicit class ManagementResponseLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, org.pytorch.serve.grpc.management.management.ManagementResponse]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, org.pytorch.serve.grpc.management.management.ManagementResponse](_l) {
    def msg: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.msg)((c_, f_) => c_.copy(msg = f_))
  }
  final val MSG_FIELD_NUMBER = 1
  def of(
    msg: _root_.scala.Predef.String
  ): _root_.org.pytorch.serve.grpc.management.management.ManagementResponse = _root_.org.pytorch.serve.grpc.management.management.ManagementResponse(
    msg
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[org.pytorch.serve.grpc.management.ManagementResponse])
}

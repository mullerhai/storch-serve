// Generated by the Scala Plugin for the Protocol Buffer Compiler.
// Do not edit!

package com.google.rpc.status

/** The `Status` type defines a logical error model that is suitable for different
  * programming environments, including REST APIs and RPC APIs. It is used by
  * [gRPC](https://github.com/grpc). The error model is designed to be:
  *
  * - Simple to use and understand for most users
  * - Flexible enough to meet unexpected needs
  *
  * # Overview
  *
  * The `Status` message contains three pieces of data: error code, error message,
  * and error details. The error code should be an enum value of
  * [google.rpc.Code][google.rpc.Code], but it may accept additional error codes if needed.  The
  * error message should be a developer-facing English message that helps
  * developers *understand* and *resolve* the error. If a localized user-facing
  * error message is needed, put the localized message in the error details or
  * localize it in the client. The optional error details may contain arbitrary
  * information about the error. There is a predefined set of error detail types
  * in the package `google.rpc` which can be used for common error conditions.
  *
  * # Language mapping
  *
  * The `Status` message is the logical representation of the error model, but it
  * is not necessarily the actual wire format. When the `Status` message is
  * exposed in different client libraries and different wire protocols, it can be
  * mapped differently. For example, it will likely be mapped to some exceptions
  * in Java, but more likely mapped to some error codes in C.
  *
  * # Other uses
  *
  * The error model and the `Status` message can be used in a variety of
  * environments, either with or without APIs, to provide a
  * consistent developer experience across different environments.
  *
  * Example uses of this error model include:
  *
  * - Partial errors. If a service needs to return partial errors to the client,
  *     it may embed the `Status` in the normal response to indicate the partial
  *     errors.
  *
  * - Workflow errors. A typical workflow has multiple steps. Each step may
  *     have a `Status` message for error reporting purpose.
  *
  * - Batch operations. If a client uses batch request and batch response, the
  *     `Status` message should be used directly inside batch response, one for
  *     each error sub-response.
  *
  * - Asynchronous operations. If an API call embeds asynchronous operation
  *     results in its response, the status of those operations should be
  *     represented directly using the `Status` message.
  *
  * - Logging. If some API errors are stored in logs, the message `Status` could
  *     be used directly after any stripping needed for security/privacy reasons.
  *
  * @param code
  *   The status code, which should be an enum value of [google.rpc.Code][google.rpc.Code].
  * @param message
  *   A developer-facing error message, which should be in English. Any
  *   user-facing error message should be localized and sent in the
  *   [google.rpc.Status.details][google.rpc.Status.details] field, or localized by the client.
  * @param details
  *   A list of messages that carry the error details.  There will be a
  *   common set of message types for APIs to use.
  */
@SerialVersionUID(0L)
final case class Status(
    code: _root_.scala.Int = 0,
    message: _root_.scala.Predef.String = "",
    details: _root_.scala.Seq[com.google.protobuf.any.Any] = _root_.scala.Seq.empty,
    unknownFields: _root_.scalapb.UnknownFieldSet = _root_.scalapb.UnknownFieldSet.empty
    ) extends scalapb.GeneratedMessage with scalapb.lenses.Updatable[Status] {
    @transient
    private[this] var __serializedSizeMemoized: _root_.scala.Int = 0
    private[this] def __computeSerializedSize(): _root_.scala.Int = {
      var __size = 0
      
      {
        val __value = code
        if (__value != 0) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeInt32Size(1, __value)
        }
      };
      
      {
        val __value = message
        if (!__value.isEmpty) {
          __size += _root_.com.google.protobuf.CodedOutputStream.computeStringSize(2, __value)
        }
      };
      details.foreach { __item =>
        val __value = __item
        __size += 1 + _root_.com.google.protobuf.CodedOutputStream.computeUInt32SizeNoTag(__value.serializedSize) + __value.serializedSize
      }
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
        val __v = code
        if (__v != 0) {
          _output__.writeInt32(1, __v)
        }
      };
      {
        val __v = message
        if (!__v.isEmpty) {
          _output__.writeString(2, __v)
        }
      };
      details.foreach { __v =>
        val __m = __v
        _output__.writeTag(3, 2)
        _output__.writeUInt32NoTag(__m.serializedSize)
        __m.writeTo(_output__)
      };
      unknownFields.writeTo(_output__)
    }
    def withCode(__v: _root_.scala.Int): Status = copy(code = __v)
    def withMessage(__v: _root_.scala.Predef.String): Status = copy(message = __v)
    def clearDetails = copy(details = _root_.scala.Seq.empty)
    def addDetails(__vs: com.google.protobuf.any.Any *): Status = addAllDetails(__vs)
    def addAllDetails(__vs: Iterable[com.google.protobuf.any.Any]): Status = copy(details = details ++ __vs)
    def withDetails(__v: _root_.scala.Seq[com.google.protobuf.any.Any]): Status = copy(details = __v)
    def withUnknownFields(__v: _root_.scalapb.UnknownFieldSet) = copy(unknownFields = __v)
    def discardUnknownFields = copy(unknownFields = _root_.scalapb.UnknownFieldSet.empty)
    def getFieldByNumber(__fieldNumber: _root_.scala.Int): _root_.scala.Any = {
      (__fieldNumber: @_root_.scala.unchecked) match {
        case 1 => {
          val __t = code
          if (__t != 0) __t else null
        }
        case 2 => {
          val __t = message
          if (__t != "") __t else null
        }
        case 3 => details
      }
    }
    def getField(__field: _root_.scalapb.descriptors.FieldDescriptor): _root_.scalapb.descriptors.PValue = {
      _root_.scala.Predef.require(__field.containingMessage eq companion.scalaDescriptor)
      (__field.number: @_root_.scala.unchecked) match {
        case 1 => _root_.scalapb.descriptors.PInt(code)
        case 2 => _root_.scalapb.descriptors.PString(message)
        case 3 => _root_.scalapb.descriptors.PRepeated(details.iterator.map(_.toPMessage).toVector)
      }
    }
    def toProtoString: _root_.scala.Predef.String = _root_.scalapb.TextFormat.printToUnicodeString(this)
    def companion: com.google.rpc.status.Status.type = com.google.rpc.status.Status
    // @@protoc_insertion_point(GeneratedMessage[google.rpc.Status])
}

object Status extends scalapb.GeneratedMessageCompanion[com.google.rpc.status.Status] {
  implicit def messageCompanion: scalapb.GeneratedMessageCompanion[com.google.rpc.status.Status] = this
  def parseFrom(`_input__`: _root_.com.google.protobuf.CodedInputStream): com.google.rpc.status.Status = {
    var __code: _root_.scala.Int = 0
    var __message: _root_.scala.Predef.String = ""
    val __details: _root_.scala.collection.immutable.VectorBuilder[com.google.protobuf.any.Any] = new _root_.scala.collection.immutable.VectorBuilder[com.google.protobuf.any.Any]
    var `_unknownFields__`: _root_.scalapb.UnknownFieldSet.Builder = null
    var _done__ = false
    while (!_done__) {
      val _tag__ = _input__.readTag()
      _tag__ match {
        case 0 => _done__ = true
        case 8 =>
          __code = _input__.readInt32()
        case 18 =>
          __message = _input__.readStringRequireUtf8()
        case 26 =>
          __details += _root_.scalapb.LiteParser.readMessage[com.google.protobuf.any.Any](_input__)
        case tag =>
          if (_unknownFields__ == null) {
            _unknownFields__ = new _root_.scalapb.UnknownFieldSet.Builder()
          }
          _unknownFields__.parseField(tag, _input__)
      }
    }
    com.google.rpc.status.Status(
        code = __code,
        message = __message,
        details = __details.result(),
        unknownFields = if (_unknownFields__ == null) _root_.scalapb.UnknownFieldSet.empty else _unknownFields__.result()
    )
  }
  implicit def messageReads: _root_.scalapb.descriptors.Reads[com.google.rpc.status.Status] = _root_.scalapb.descriptors.Reads{
    case _root_.scalapb.descriptors.PMessage(__fieldsMap) =>
      _root_.scala.Predef.require(__fieldsMap.keys.forall(_.containingMessage eq scalaDescriptor), "FieldDescriptor does not match message type.")
      com.google.rpc.status.Status(
        code = __fieldsMap.get(scalaDescriptor.findFieldByNumber(1).get).map(_.as[_root_.scala.Int]).getOrElse(0),
        message = __fieldsMap.get(scalaDescriptor.findFieldByNumber(2).get).map(_.as[_root_.scala.Predef.String]).getOrElse(""),
        details = __fieldsMap.get(scalaDescriptor.findFieldByNumber(3).get).map(_.as[_root_.scala.Seq[com.google.protobuf.any.Any]]).getOrElse(_root_.scala.Seq.empty)
      )
    case _ => throw new RuntimeException("Expected PMessage")
  }
  def javaDescriptor: _root_.com.google.protobuf.Descriptors.Descriptor = com.google.rpc.status.StatusProto.javaDescriptor.getMessageTypes().get(0)
  def scalaDescriptor: _root_.scalapb.descriptors.Descriptor = com.google.rpc.status.StatusProto.scalaDescriptor.messages(0)
  def messageCompanionForFieldNumber(__number: _root_.scala.Int): _root_.scalapb.GeneratedMessageCompanion[?] = {
    var __out: _root_.scalapb.GeneratedMessageCompanion[?] = null
    (__number: @_root_.scala.unchecked) match {
      case 3 => __out = com.google.protobuf.any.Any
    }
    __out
  }
  lazy val nestedMessagesCompanions: Seq[_root_.scalapb.GeneratedMessageCompanion[? <: _root_.scalapb.GeneratedMessage]] = Seq.empty
  def enumCompanionForFieldNumber(__fieldNumber: _root_.scala.Int): _root_.scalapb.GeneratedEnumCompanion[?] = throw new MatchError(__fieldNumber)
  lazy val defaultInstance = com.google.rpc.status.Status(
    code = 0,
    message = "",
    details = _root_.scala.Seq.empty
  )
  implicit class StatusLens[UpperPB](_l: _root_.scalapb.lenses.Lens[UpperPB, com.google.rpc.status.Status]) extends _root_.scalapb.lenses.ObjectLens[UpperPB, com.google.rpc.status.Status](_l) {
    def code: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Int] = field(_.code)((c_, f_) => c_.copy(code = f_))
    def message: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Predef.String] = field(_.message)((c_, f_) => c_.copy(message = f_))
    def details: _root_.scalapb.lenses.Lens[UpperPB, _root_.scala.Seq[com.google.protobuf.any.Any]] = field(_.details)((c_, f_) => c_.copy(details = f_))
  }
  final val CODE_FIELD_NUMBER = 1
  final val MESSAGE_FIELD_NUMBER = 2
  final val DETAILS_FIELD_NUMBER = 3
  def of(
    code: _root_.scala.Int,
    message: _root_.scala.Predef.String,
    details: _root_.scala.Seq[com.google.protobuf.any.Any]
  ): _root_.com.google.rpc.status.Status = _root_.com.google.rpc.status.Status(
    code,
    message,
    details
  )
  // @@protoc_insertion_point(GeneratedMessageCompanion[google.rpc.Status])
}

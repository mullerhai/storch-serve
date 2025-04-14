package org.pytorch.serve.util.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder
import java.nio.charset.StandardCharsets
import java.util
import org.pytorch.serve.util.messages.BaseModelRequest
import org.pytorch.serve.util.messages.InputParameter
import org.pytorch.serve.util.messages.ModelInferenceRequest
import org.pytorch.serve.util.messages.ModelLoadModelRequest
import org.pytorch.serve.util.messages.RequestInput
import scala.jdk.CollectionConverters._
@ChannelHandler.Sharable object ModelRequestEncoder {
  private def encodeField(field: CharSequence, out: ByteBuf): Unit = {
    if (field == null) {
      out.writeInt(0)
      return
    }
    val buf = field.toString.getBytes(StandardCharsets.UTF_8)
    out.writeInt(buf.length)
    out.writeBytes(buf)
  }
}

@ChannelHandler.Sharable class ModelRequestEncoder(preferDirect: Boolean) extends MessageToByteEncoder[BaseModelRequest](preferDirect) {
  override protected def encode(ctx: ChannelHandlerContext, msg: BaseModelRequest, out: ByteBuf): Unit = {
    if (msg.isInstanceOf[ModelLoadModelRequest]) {
      out.writeByte('L')
      val request = msg.asInstanceOf[ModelLoadModelRequest]
      var buf = msg.getModelName.getBytes(StandardCharsets.UTF_8)
      out.writeInt(buf.length)
      out.writeBytes(buf)
      buf = request.getModelPath.getBytes(StandardCharsets.UTF_8)
      out.writeInt(buf.length)
      out.writeBytes(buf)
      var batchSize = request.getBatchSize
      if (batchSize <= 0) batchSize = 1
      out.writeInt(batchSize)
      val handler = request.getHandler
      if (handler != null) buf = handler.getBytes(StandardCharsets.UTF_8)
      // TODO: this might be a bug. If handler isn't specified, this
      // will repeat the model path
      out.writeInt(buf.length)
      out.writeBytes(buf)
      out.writeInt(request.getGpuId)
      val envelope = request.getEnvelope
      if (envelope != null) buf = envelope.getBytes(StandardCharsets.UTF_8)
      else buf = new Array[Byte](0)
      out.writeInt(buf.length)
      out.writeBytes(buf)
      out.writeBoolean(request.isLimitMaxImagePixels)
    }
    else if (msg.isInstanceOf[ModelInferenceRequest]) {
      out.writeByte('I')
      val request = msg.asInstanceOf[ModelInferenceRequest]
//      import scala.collection.JavaConversions._
      for (input <- request.getRequestBatch.asScala) {
        encodeRequest(input, out)
      }
      out.writeInt(-1) // End of List
    }
  }

  private def encodeRequest(req: RequestInput, out: ByteBuf): Unit = {
    val buf = req.getRequestId.getBytes(StandardCharsets.UTF_8)
    out.writeInt(buf.length)
    out.writeBytes(buf)
//    import scala.collection.JavaConversions._
    for (entry <- req.getHeaders.entrySet.asScala) {
      ModelRequestEncoder.encodeField(entry.getKey, out)
      ModelRequestEncoder.encodeField(entry.getValue, out)
    }
    out.writeInt(-1) // End of List
    if (req.isCachedInBackend) {
      out.writeInt(-1) // End of List
      return
    }
//    import scala.collection.JavaConversions._
    for (input <- req.getParameters.asScala) {
      encodeParameter(input, out)
    }
    out.writeInt(-1) // End of List
  }

  private def encodeParameter(parameter: InputParameter, out: ByteBuf): Unit = {
    val modelInputName = parameter.getName.getBytes(StandardCharsets.UTF_8)
    out.writeInt(modelInputName.length)
    out.writeBytes(modelInputName)
    ModelRequestEncoder.encodeField(parameter.getContentType, out)
    val buf = parameter.getValue
    out.writeInt(buf.length)
    out.writeBytes(buf)
  }
}
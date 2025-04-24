package org.pytorch.serve.util.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.NotEnoughDataDecoderException
import org.pytorch.serve.util.messages.{ModelWorkerResponse, Predictions}

import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*
class ModelResponseDecoder(private val maxBufferSize: Int) extends ByteToMessageDecoder {
  override protected def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    val size = in.readableBytes
    if (size < 9) return
    in.markReaderIndex
    var completed = false
    try {
      val resp = new ModelWorkerResponse
      // Get Response overall Code
      resp.setCode(in.readInt)
      var len = CodecUtils.readLength(in, maxBufferSize)
      if (len == CodecUtils.BUFFER_UNDER_RUN) return
      resp.setMessage(CodecUtils.readString(in, len))
      val predictions = new ListBuffer[Predictions]
      while (len  != CodecUtils.END) {
        if (len == CodecUtils.BUFFER_UNDER_RUN) return
        val prediction = new Predictions
        // Set response RequestId
        prediction.setRequestId(CodecUtils.readString(in, len))
        len = CodecUtils.readLength(in, maxBufferSize)
        if (len == CodecUtils.BUFFER_UNDER_RUN) return
        // Set content type
        prediction.setContentType(CodecUtils.readString(in, len))
        // Set per request response code
        if (in.readableBytes < 4) return
        val httpStatusCode = in.readInt
        prediction.setStatusCode(httpStatusCode)
        // Set the actual message
        len = CodecUtils.readLength(in, maxBufferSize)
        if (len == CodecUtils.BUFFER_UNDER_RUN) return
        prediction.setReasonPhrase(CodecUtils.readString(in, len))
        len = CodecUtils.readLength(in, maxBufferSize)
        if (len == CodecUtils.BUFFER_UNDER_RUN) return
        prediction.setHeaders(CodecUtils.readMap(in, len))
        len = CodecUtils.readLength(in, maxBufferSize)
        if (len == CodecUtils.BUFFER_UNDER_RUN) return
        prediction.setResp(CodecUtils.read(in, len))
        predictions.append(prediction)
        len = CodecUtils.readLength(in, maxBufferSize)
      }
      resp.setPredictions(predictions.toList)
      out.add(resp)
      completed = true
    } catch {
      case e: HttpPostRequestDecoder.NotEnoughDataDecoderException =>
    } finally if (!completed) in.resetReaderIndex
  }
}
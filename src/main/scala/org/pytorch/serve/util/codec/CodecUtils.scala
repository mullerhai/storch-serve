package org.pytorch.serve.util.codec

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.TooLongFrameException
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.NotEnoughDataDecoderException

import java.nio.charset.StandardCharsets
import java.util
import scala.jdk.CollectionConverters.*
object CodecUtils {
  val END: Int = -1
  val BUFFER_UNDER_RUN: Int = -3
  val TIMEOUT_IN_MILLIS = 100

  def readLength(byteBuf: ByteBuf, maxLength: Int): Int = {
    val size = byteBuf.readableBytes
    if (size < 4) throw new HttpPostRequestDecoder.NotEnoughDataDecoderException("Did not receive enough data.")
    val len = byteBuf.readInt
    if (len > maxLength) throw new TooLongFrameException("Message size exceed limit: " + len + "\nConsider increasing the 'max_response_size' in 'config.properties' to fix.")
    if (len > byteBuf.readableBytes) throw new HttpPostRequestDecoder.NotEnoughDataDecoderException("Did not receive enough data.")
    len
  }

  def readString(byteBuf: ByteBuf, len: Int) = new String(read(byteBuf, len), StandardCharsets.UTF_8)

  def read(in: ByteBuf, len: Int): Array[Byte] = {
    if (len < 0) throw new HttpPostRequestDecoder.NotEnoughDataDecoderException("Did not receive enough data.")
    val buf = new Array[Byte](len)
    in.readBytes(buf)
    buf
  }

  def readMap(in: ByteBuf, lenz: Int): util.Map[String, String] = {
    var len =lenz
    val ret = new util.HashMap[String, String]
    while (len > 0) {
      var l = readLength(in, 6500000) // We replace len here with 6500000 as a workaround before we
      // can fix the whole otf. Basically, were mixing up bytes
      // (expected by readLength) and number of entries (given to
      // readMap). If we only have a small number of entries our
      // values in the map are not allowed to be very big as we
      // compare the given number of entries with the byte size
      // we're expecting after reading the length of the next
      // message.
      val key = readString(in, l)
      l = readLength(in, 6500000)
      val `val` = readString(in, l)
      ret.put(key, `val`)
      len -= 1
    }
    ret
  }
}

final class CodecUtils private {
}
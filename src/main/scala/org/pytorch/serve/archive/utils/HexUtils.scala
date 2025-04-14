package org.pytorch.serve.archive.utils

object HexUtils {
  private val HEX_CHARS = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  def toHexString(block: Array[Byte]): String = toHexString(block, 0, block.length)

  def toHexString(block: Array[Byte], offset: Int, len: Int): String = {
    if (block == null) return null
    if (offset < 0 || offset + len > block.length) throw new IllegalArgumentException("Invalid offset or length.")
    val buf = new StringBuilder
    var i = offset
    val size = offset + len
    while (i < size) {
      val high = (block(i) & 0xf0) >> 4
      val low = block(i) & 0x0f
      buf.append(HEX_CHARS(high))
      buf.append(HEX_CHARS(low))
      i += 1
    }
    buf.toString
  }
}

//final class HexUtils private {
//}
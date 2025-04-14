package org.pytorch.serve.archive.model.s3

import java.util.Locale

/** Utilities for encoding and decoding binary data to and from different forms. */
object BinaryUtils {
  /**
   * Converts byte data to a Hex-encoded string.
   *
   * @param data data to hex encode.
   * @return hex-encoded string.
   */
    def toHex(data: Array[Byte]): String = {
      val sb = new StringBuilder(data.length * 2)
      for (b <- data) {
        var hex = Integer.toHexString(b)
        if (hex.length == 1) {
          // Append leading zero.
          sb.append('0')
        }
        else if (hex.length == 8) {
          // Remove ff prefix from negative numbers.
          hex = hex.substring(6)
        }
        sb.append(hex)
      }
      sb.toString.toLowerCase(Locale.getDefault)
    }

  /**
   * Converts a Hex-encoded data string to the original byte data.
   *
   * @param hexData hex-encoded data to decode.
   * @return decoded data from the hex string.
   */
  def fromHex(hexData: String): Array[Byte] = {
    val result = new Array[Byte]((hexData.length + 1) / 2)
    var hexNumber: String = null
    var stringOffset = 0
    var byteOffset = 0
    while (stringOffset < hexData.length) {
      hexNumber = hexData.substring(stringOffset, stringOffset + 2)
      stringOffset += 2
      result({
        byteOffset += 1; byteOffset - 1
      }) = Integer.parseInt(hexNumber, 16).toByte
    }
    result
  }
}

//final class BinaryUtils private {
//}
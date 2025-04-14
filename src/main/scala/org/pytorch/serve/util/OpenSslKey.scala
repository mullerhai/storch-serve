package org.pytorch.serve.util

import scala.jdk.CollectionConverters._
import scala.util.control.Breaks.{break, breakable}
/** A utility class converting OpenSSL private key to PKCS8 private key. */
object OpenSslKey {
  private val RSA_ENCRYPTION = Array(1, 2, 840, 113549, 1, 1, 1)
  private val NULL_BYTES: Array[Byte] = Array(0x05, 0x00)

  /**
   * Convert OpenSSL private key to PKCS8 private key.
   *
   * @param keySpec OpenSSL key spec
   * @return PKCS8 encoded private key
   */
  def convertPrivateKey(keySpec: Array[Byte]): Array[Byte] = {
    if (keySpec == null) return null
    val bytes = new Array[Byte](keySpec.length)
    System.arraycopy(keySpec, 0, bytes, 0, keySpec.length)
    val octetBytes = encodeOctetString(bytes)
    val oidBytes = encodeOID(RSA_ENCRYPTION)
    val verBytes: Array[Byte] = Array(0x02, 0x01, 0x00)
    val seqBytes = new Array[Array[Byte]](4)
    seqBytes(0) = oidBytes
    seqBytes(1) = NULL_BYTES
    seqBytes(2) = null
    val oidSeqBytes = encodeSequence(seqBytes)
    seqBytes(0) = verBytes
    seqBytes(1) = oidSeqBytes
    seqBytes(2) = octetBytes
    seqBytes(3) = null
    encodeSequence(seqBytes)
  }

  private def encodeOID(oid: Array[Int]): Array[Byte] = {
    if (oid == null) return null
    var oLen = 1
    for (i <- 2 until oid.length) {
      oLen += getOIDCompLength(oid(i))
    }
    val len = oLen + getLengthOfLengthField(oLen) + 1
    val bytes = new Array[Byte](len)
    bytes(0) = 0x06 // ASN Object ID
    var offset = writeLengthField(bytes, oLen)
    bytes({
      offset += 1; offset - 1
    }) = (40 * oid(0) + oid(1)).toByte
    for (i <- 2 until oid.length) {
      offset = writeOIDComp(oid(i), bytes, offset)
    }
    bytes
  }

  private def encodeOctetString(bytes: Array[Byte]): Array[Byte] = {
    if (bytes == null) return null
    val oLen = bytes.length // one byte for unused bits field
    val len = oLen + getLengthOfLengthField(oLen) + 1
    val newBytes = new Array[Byte](len)
    newBytes(0) = 0x04
    val offset = writeLengthField(newBytes, oLen)
    if (len - oLen != offset) return null
    System.arraycopy(bytes, 0, newBytes, offset, oLen)
    newBytes
  }

  private def encodeSequence(byteArrays: Array[Array[Byte]]): Array[Byte] = {
    if (byteArrays == null) return null
    var oLen = 0
    breakable(
      for (b <- byteArrays) {
        if (b == null) break() //todo: break is not supported
        oLen += b.length
      }
    )

    val len = oLen + getLengthOfLengthField(oLen) + 1
    val bytes = new Array[Byte](len)
    bytes(0) = 0x10 | 0x20 // ASN sequence & constructed
    var offset = writeLengthField(bytes, oLen)
    if (len - oLen != offset) return null
    breakable(
      for (b <- byteArrays) {
        if (b == null) break() //todo: break is not supported
        System.arraycopy(b, 0, bytes, offset, b.length)
        offset += b.length
      }
    )

    bytes
  }

  private def writeLengthField(bytes: Array[Byte], len: Int): Int = {
    if (len < 127) {
      bytes(1) = len.toByte
      return 2
    }
    val lenOfLenField = getLengthOfLengthField(len)
    bytes(1) = ((lenOfLenField - 1) | 0x80).toByte // record length of the length field
    for (i <- lenOfLenField to 2 by -1) { // write the length
      bytes(i) = (len >> ((lenOfLenField - i) * 8)).toByte
    }
    lenOfLenField + 1
  }

  private def getLengthOfLengthField(len: Int) = if (len <= 127) { // highest bit is zero, one byte is enough
    1
  }
  else if (len <= 0xFF) { // highest bit is 1, two bytes in the form {0x81, 0xab}
    2
  }
  else if (len <= 0xFFFF) { // three bytes in the form {0x82, 0xab, 0xcd}
    3
  }
  else if (len <= 0xFFFFFF) { // four bytes in the form {0x83, 0xab, 0xcd, 0xef}
    4
  }
  else { // five bytes in the form {0x84, 0xab, 0xcd, 0xef, 0xgh}
    5
  }

  private def getOIDCompLength(comp: Int) = if (comp <= 0x7F) 1
  else if (comp <= 0x3FFF) 2
  else if (comp <= 0x1FFFFF) 3
  else if (comp <= 0xFFFFFFF) 4
  else 5

  private def writeOIDComp(comp: Int, bytes: Array[Byte], offset: Int) = {
    val len = getOIDCompLength(comp)
    var off = offset
    for (i <- len - 1 until 0 by -1) {
      bytes({
        off += 1; off - 1
      }) = ((comp >>> i * 7) | 0x80).toByte
    }
    bytes({
      off += 1; off - 1
    }) = (comp & 0x7F).toByte
    off
  }
}

final class OpenSslKey private {
}
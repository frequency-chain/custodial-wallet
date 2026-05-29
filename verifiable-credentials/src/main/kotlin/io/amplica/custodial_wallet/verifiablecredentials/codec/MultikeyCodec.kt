package io.amplica.custodial_wallet.verifiablecredentials.codec

import org.apache.commons.codec.binary.Hex


enum class KeyType(val prefixHex: String, val keySize: Int) {
  ECDSA("8024", 33),
  Ed25519("ed01", 32),
  Sr25519("ef01", 32),
}

/**
 * Provides encoding/decoding facilities between `ByteArray`'s and the so-called 'multikey' `String` serialization
 * format referenced by the [W3C EdDSA data integrity specification](https://www.w3.org/TR/vc-di-eddsa/#multikey).
 *
 * See also: [https://www.w3.org/TR/controller-document/#multikey](https://www.w3.org/TR/controller-document/#multikey)
 */
class MultikeyCodec(
  private val keyType: KeyType,
  private val base58MultibaseCodec: Codec<ByteArray, String>
) : Codec<ByteArray, String> {

  companion object {
    const val PREFIX_SIZE = 2
  }

  private val algorithmPrefix: ByteArray = Hex.decodeHex(keyType.prefixHex)

  override fun encode(input: ByteArray): String {
    if (input.size != keyType.keySize) {
      throw IllegalArgumentException("Invalid key data length: ${input.size} (expected ${keyType.keySize} bytes)")
    }

    return base58MultibaseCodec.encode(algorithmPrefix + input)
  }

  override fun decode(input: String): ByteArray {
    val decoded = base58MultibaseCodec.decode(input)

    if (decoded.size != (PREFIX_SIZE + keyType.keySize)) {
      throw IllegalArgumentException("Invalid decoded data length: ${decoded.size} (expected 34 bytes)")
    }

    val prefix = decoded.sliceArray(0..<PREFIX_SIZE)

    if (!prefix.contentEquals(algorithmPrefix)) {
      val prefixHex = Hex.encodeHexString(prefix)
      throw IllegalArgumentException("Unexpected key discriminator: 0x$prefixHex (expected '0x${keyType.prefixHex}')")
    }

    val data = decoded.drop(PREFIX_SIZE).toByteArray()

    return data
  }

}

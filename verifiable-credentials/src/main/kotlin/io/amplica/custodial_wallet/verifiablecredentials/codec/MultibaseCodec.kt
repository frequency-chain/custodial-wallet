package io.amplica.custodial_wallet.verifiablecredentials.codec

import org.bitcoinj.base.Base58


/**
 * Provides encoding/decoding facilities between `ByteArray`'s and the so-called 'multibase' `String` serialization
 * scheme described in [this W3C specification](https://www.w3.org/TR/controller-document/#multibase-0).
 */
object Base58MultibaseCodec : Codec<ByteArray, String> {

  private const val PREFIX = "z"

  override fun encode(input: ByteArray): String {
    return PREFIX + Base58.encode(input)
  }

  override fun decode(input: String): ByteArray {
    val prefix = input.firstOrNull() ?: throw IllegalArgumentException("The empty string cannot be decoded")

    if (prefix.toString() != PREFIX) {
      throw IllegalArgumentException(
        "Unexpectedly found prefix '$prefix' when attempting to decode a base-58 multibase string (prefix must be '${PREFIX}')"
      )
    }

    val data = input.drop(1)

    return Base58.decode(data)
  }

}

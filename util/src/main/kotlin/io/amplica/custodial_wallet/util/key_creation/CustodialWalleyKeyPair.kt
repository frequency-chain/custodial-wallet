package io.amplica.custodial_wallet.util.key_creation

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.util.base64UrlEncode
import io.amplica.custodial_wallet.util.toBase58AddressFormat
import io.amplica.custodial_wallet.util.toHex

typealias PublicKeyBytes = ByteArray
typealias PrivateKeyBytes = ByteArray
typealias SignatureBytes = ByteArray
typealias PayloadBytes = ByteArray


abstract class KeyPairBytes(
  open val publicKeyBytes: PublicKeyBytes,
  open val privateKeyBytes: PrivateKeyBytes,
  open val keyPairType: KeyPairType,
  open val algo: KeyPairSignatureAlgorithm
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is KeyPairBytes) return false

    if (!publicKeyBytes.contentEquals(other.publicKeyBytes)) return false
    if (!privateKeyBytes.contentEquals(other.privateKeyBytes)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = publicKeyBytes.contentHashCode()
    result = 31 * result + privateKeyBytes.contentHashCode()
    return result
  }

  fun toKeyPairDto(
    encoding: Encoding = Encoding.HEX,
    publicKeyFormat: PublicKeyFormat = PublicKeyFormat.BARE,
    sS58AddressFormat: SS58AddressFormat = SS58AddressFormat.SUBSTRATE_ACCOUNT
  ): KeyPairDto {
    val encodedPublicKey = when(encoding) {
      Encoding.HEX -> toHex(publicKeyBytes)
      Encoding.BASE_58 -> toBase58AddressFormat(publicKeyBytes, sS58AddressFormat)
      Encoding.BASE64_URLENCODED -> base64UrlEncode(publicKeyBytes)
    }
    val encodedPrivateKey = when(encoding) {
      Encoding.HEX -> toHex(privateKeyBytes)
      Encoding.BASE_58 -> toBase58AddressFormat(privateKeyBytes, sS58AddressFormat)
      Encoding.BASE64_URLENCODED -> base64UrlEncode(privateKeyBytes)
    }
    return KeyPairDto(
      encodedPublicKey,
      encodedPrivateKey,
      encoding,
      publicKeyFormat,
      keyPairType
    )
  }

  fun toPublicKeyDto(sS58AddressFormat: SS58AddressFormat): PublicKeyDto {
    return when (this.keyPairType) {
      KeyPairType.SR25519 -> Sr25519KeyPairCreator.createSr25519PublicKeyDto(this, sS58AddressFormat)
      KeyPairType.SECP256K1 -> {
        PublicKeyDto(
          toHex(publicKeyBytes),
          Encoding.HEX,
          PublicKeyFormat.BARE,
          keyPairType
        )
      }
      else -> throw UnsupportedOperationException("KeyPairType ${this.keyPairType} is not supported for conversion to a PublicKeyDto")
    }
  }

}

interface KeyPairCreator {
  fun createKeyPair() : KeyPairBytes
}

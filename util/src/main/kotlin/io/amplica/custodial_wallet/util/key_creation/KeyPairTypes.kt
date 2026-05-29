package io.amplica.custodial_wallet.util.key_creation

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.google.common.collect.FluentIterable
import io.amplica.custodial_wallet.util.base58DecodeAndExtractPublicKey
import io.amplica.custodial_wallet.util.decodeValueToBytes
import java.util.*


enum class PublicKeyFormat(@JsonValue val format: String) {
  SS58("ss58"),
  DSNP_PUBLIC_KEY("dsnpPublicKey"),
  BARE("bare"),
  COMPRESSED_HEX("compressedHex");

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val FORMAT_INDEX: Map<String, PublicKeyFormat> = FluentIterable.from(entries.toTypedArray()).uniqueIndex { it.format.uppercase(
      Locale.US) }

    @JvmStatic
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromFormat(format: String): PublicKeyFormat {
      return FORMAT_INDEX[format.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Format=${format} is not recognized")
    }
  }
}

enum class SignatureKeyPairType(@JsonValue val type: String) {
  SR25519("SR25519"),
  X25519("X25519"),
  PASSKEY_COMPRESSED("P256_COMPRESSED"),
  SECP256K1("SECP256K1"),
  ED25519("ED25519"),
  UNSUPPORTED("UNSUPPORTED");

  companion object {
    @Suppress("UnstableApiUsage")
    private val TYPE_INDEX: Map<String, SignatureKeyPairType> = FluentIterable.from(entries.toTypedArray()).uniqueIndex { it.type.uppercase(
      Locale.US) }

    @JvmStatic
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromType(type: String): SignatureKeyPairType {
      return TYPE_INDEX[type.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Type=${type} is not recognized")
    }
  }

  fun toKeyPairSignatureAlgorithm(): KeyPairSignatureAlgorithm {
    return when(this) {
      SR25519 -> KeyPairSignatureAlgorithm.SR25519
      else -> throw IllegalArgumentException("KeyPairType $this is not supported, only KeyPairSignatureAlgorithms ${KeyPairSignatureAlgorithm.entries.toTypedArray()}")
    }
  }
}

/**
 * This seems redundant but this represents the KeyPair type and not the signature which need not be 1 to 1. Also, this
 * enum likes in the Custodial Wallet application whereas the Signature algorithm from the Signing service is going
 * to be in it's own repo some day so this is to make sure that can still happen.
 *
 * @property type
 * @constructor Create empty Key pair type
 */
enum class KeyPairType(@JsonValue val type: String, val backwardsCompatibilityValue: String, val signatureKeyPairType: SignatureKeyPairType) {
  SR25519("Sr25519", "SR25519", SignatureKeyPairType.SR25519),
  X25519("X25519", "X25519", SignatureKeyPairType.X25519),
  PASSKEY_COMPRESSED("P256_COMPRESSED", "P256_COMPRESSED", SignatureKeyPairType.PASSKEY_COMPRESSED),
  SECP256K1("SECP256K1", "SECP256K1", SignatureKeyPairType.SECP256K1),
  ED25519("Ed25519", "ED25519", SignatureKeyPairType.X25519),
  ;

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val TYPE_INDEX: Map<String, KeyPairType> = FluentIterable.from(values()).uniqueIndex { it.type.uppercase(
      Locale.US) }

    @JvmStatic
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromType(type: String): KeyPairType {
      return TYPE_INDEX[type.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Type=${type} is not recognized")
    }
  }

  fun toKeyPairSignatureAlgorithm(): KeyPairSignatureAlgorithm {
    return when(this) {
      SR25519 -> KeyPairSignatureAlgorithm.SR25519
      else -> throw IllegalArgumentException("KeyPairType $this is not supported, only KeyPairSignatureAlgorithms ${KeyPairSignatureAlgorithm.entries.toTypedArray()}")
    }
  }
}

enum class Encoding(@JsonValue val encoding: String) {
  HEX("base16"),
  BASE_58("base58"),
  BASE64_URLENCODED("base64urlencoded");

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val ENCODING_INDEX: Map<String, Encoding> = FluentIterable.from(values()).uniqueIndex { it.encoding.uppercase(
      Locale.US) }

    @JvmStatic
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    fun fromEncoding(format: String): Encoding {
      return ENCODING_INDEX[format.uppercase(Locale.US)]
        ?: throw IllegalArgumentException("Encoding=${format} is not recognized")
    }
  }
}

data class EncodedBytes(
  @JsonProperty("encodedValue") val encodedValue: String,
  @JsonProperty("encoding") val encoding: Encoding,
) {
  fun toByteArray(): ByteArray {
    return decodeValueToBytes(encodedValue, encoding)
  }
}

data class PublicKeyDto(
  @JsonProperty("encodedValue") val encodedValue: String,
  @JsonProperty("encoding") val encoding: Encoding,
  @JsonProperty("format") val format: PublicKeyFormat,
  @JsonProperty("type") val type: KeyPairType
) {
  fun toPublicKeyBytes(): PublicKeyBytes {
    return when (format) {
      PublicKeyFormat.SS58 -> {
        if (encoding != Encoding.BASE_58) {
          throw IllegalArgumentException("For format $format encoding $encoding is invalid only ${Encoding.BASE_58} is supported")
        }
        base58DecodeAndExtractPublicKey(encodedValue)
      }

      else -> decodeValueToBytes(encodedValue, encoding)
    }
  }
}

data class KeyPairDto(
  @JsonProperty("encodedPublicKeyValue") val encodedPublicKeyValue: String,
  @JsonProperty("encodedPrivateKeyValue") val encodedPrivateKeyValue: String,
  @JsonProperty("encoding") val encoding: Encoding,
  @JsonProperty("format") val format: PublicKeyFormat,
  @JsonProperty("type") val type: KeyPairType
)
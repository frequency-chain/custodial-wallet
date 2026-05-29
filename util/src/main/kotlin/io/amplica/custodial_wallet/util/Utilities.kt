package io.amplica.custodial_wallet.util

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import com.strategyobject.substrateclient.crypto.ss58.SS58Codec
import com.strategyobject.substrateclient.scale.writers.HeterogeneousVecWriter
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toPrivateKeyBytes
import io.amplica.frequency.crypto.toPublicKeyBytes
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.apache.commons.codec.binary.Hex
import org.slf4j.Logger
import org.slf4j.event.Level
import java.net.InetAddress
import java.net.URI
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*


fun normalizeToHex(publicKeyDto: PublicKeyDto): String {
  // Handle ss58
  val bytes = when {
    publicKeyDto.format == PublicKeyFormat.SS58 && publicKeyDto.encoding == Encoding.BASE_58 -> base58DecodeAndExtractPublicKey(publicKeyDto.encodedValue)
    else -> decodeValueToBytes(publicKeyDto.encodedValue, publicKeyDto.encoding)
  }
  return when (publicKeyDto.encoding) {
    Encoding.BASE_58, Encoding.BASE64_URLENCODED -> toHex(bytes)
    Encoding.HEX -> normalizeHex(publicKeyDto.encodedValue) // This is a reversion to not change behavior any worse than it already is
  }
}

/**
 * ideally don't call this function directly, add a `toByteArray`, or the like, method to the data structure you
 * are dealing with, you may find it already has one.
 *
 * @see PublicKeyDto.toPublicKeyBytes
 * @see io.amplica.custodial_wallet.util.key_creation.EncodedBytes.toByteArray
 * @see io.amplica.custodial_wallet.client.redis.dto.Signature.toSignatureBytes
 */
fun decodeValueToBytes(value: String, encoding: Encoding) : ByteArray{
  return when(encoding){
    Encoding.BASE_58 -> throw UnsupportedOperationException("Base58 is intentionally not supported, are you sure you don't want base58DecodeAndExtractPublicKey?")
    Encoding.HEX -> fromHex(value)
    Encoding.BASE64_URLENCODED -> fromBase64Url(value)
  }
}

/**
 * Encoding in HEX or BASE64 requires no input beyond the ByteArray to encode
 * Encoding BASE58 requires SS58AddressFormat for Polkadot which is dependent on how/where you plan to use it.
 * By default, this is set to SUBSTRATE_ACCOUNT. Only adjust this (only use it at all) if you know why you are doing so.
 */
fun encodeValueFromBytes(value: ByteArray, encoding: Encoding, addressFormat: SS58AddressFormat = SS58AddressFormat.SUBSTRATE_ACCOUNT): String {
  return when(encoding){
    Encoding.BASE_58 -> toBase58AddressFormat(value, addressFormat)
    Encoding.HEX -> toHex(value)
    Encoding.BASE64_URLENCODED -> base64UrlEncode(value)
  }
}

fun switchEncoding(value: String, fromEncoding: Encoding, toEncoding: Encoding, addressFormat: SS58AddressFormat = SS58AddressFormat.SUBSTRATE_ACCOUNT): String {
  val decodedBytes = decodeValueToBytes(value, fromEncoding)
  return encodeValueFromBytes(decodedBytes, toEncoding, addressFormat)
}

const val HEX_PREFIX: String = "0x"

fun toHex(byteArray: ByteArray): String {
  //This always lowercases; encoding is case insensitive as the JVM is case insensitive for radix 16
  var encodedString: String = Hex.encodeHexString(byteArray)
  if(encodedString.startsWith(HEX_PREFIX)) {
    return encodedString
  } else {
    return HEX_PREFIX + encodedString
  }
}

fun ByteArray.toHexString(): String {
  return toHex(this)
}

fun normalizeHex(hex: String): String {
  return if (!hex.startsWith(HEX_PREFIX)) {
    "0x$hex"
  } else {
    hex
  }
}

fun stripHexPrefix(hex: String): String {
  return hex.removePrefix(HEX_PREFIX)
}

fun fromHex(hex: String): ByteArray {
  var hexToDecode = hex
  if(hex.startsWith(HEX_PREFIX)) {
    hexToDecode = hex.substringAfter(HEX_PREFIX)
  }

  return Hex.decodeHex(hexToDecode)
}

fun toBase58AddressFormat(bytes: ByteArray, addressFormat: SS58AddressFormat): String {
  return SS58Codec.encode(bytes, addressFormat)
}

fun base58DecodeAndExtractPublicKey(base58EncodedValue: String): ByteArray {
  val providerSS58AddressBytes = SS58Codec.decode(base58EncodedValue)
  return providerSS58AddressBytes.address
}

fun base58Decode(input: String): ByteArray {
  if (input.length == 0) {
    return ByteArray(0)
  }
  // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
  val INDEXES = fillIndexes()
  val input58 = ByteArray(input.length)
  for (i in 0 until input.length) {
    val c = input[i]
    val digit = if (c.code < 128) INDEXES.get(c.code) else -1
    check(digit >= 0) { "InvalidCharacter in base 58" }
    input58[i] = digit.toByte()
  }
  // Count leading zeros.
  var zeros = 0
  while (zeros < input58.size && input58[zeros].toInt() == 0) {
    ++zeros
  }
  // Convert base-58 digits to base-256 digits.
  val decoded = ByteArray(input.length)
  var outputStart = decoded.size
  var inputStart = zeros
  while (inputStart < input58.size) {
    decoded[--outputStart] = divmod(input58, inputStart, 58, 256)
    if (input58[inputStart].toInt() == 0) {
      ++inputStart // optimization - skip leading zeros
    }
  }
  // Ignore extra leading zeroes that were added during the calculation.
  while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) {
    ++outputStart
  }
  // Return decoded data (including original number of leading zeros).
  return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.size)
}

fun fillIndexes(): IntArray {
  val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray()
  val INDEXES = IntArray(128)
  Arrays.fill(INDEXES, -1)
  for (i in ALPHABET.indices) {
    INDEXES[ALPHABET[i].code] = i
  }

  return INDEXES
}

private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Byte {
  // this is just long division which accounts for the base of the input digits
  var remainder = 0
  for (i in firstDigit until number.size) {
    val digit = number[i].toInt() and 0xFF
    val temp = remainder * base + digit
    number[i] = (temp / divisor).toByte()
    remainder = temp % divisor
  }
  return remainder.toByte()
}

fun ss58AddressFormatFromByte(ss58TypeByte: Byte): SS58AddressFormat {
  return when (ss58TypeByte) {
    0.toByte() -> SS58AddressFormat.POLKADOT_ACCOUNT
    2.toByte() -> SS58AddressFormat.KUSAMA_ACCOUNT
    42.toByte() -> SS58AddressFormat.SUBSTRATE_ACCOUNT
    else -> SS58AddressFormat.SUBSTRATE_ACCOUNT
  }
}

fun publicKeyToUniversalAddress(publicKey: PublicKeyBytes, keyPairType: KeyPairType): ByteArray {
  return when (keyPairType) {
    KeyPairType.SR25519 -> publicKey
    KeyPairType.SECP256K1 -> Secp256K1CryptoProvider.toUniversalAddress(publicKey.toPublicKeyBytes())
    else -> throw UnsupportedOperationException("KeyPairType $keyPairType cannot be converted to a universal address")
  }
}

fun keyPairBytesToUniversalAddress(keyPairBytes: KeyPairBytes): ByteArray {
  return publicKeyToUniversalAddress(keyPairBytes.publicKeyBytes, keyPairBytes.keyPairType)
}

fun keyPairBytesToAccountKeyPair(keyPairBytes: KeyPairBytes): AccountKeyPair {
  val cryptoProvider = when (keyPairBytes.keyPairType) {
    KeyPairType.SR25519 -> Sr25519CryptoProvider
    KeyPairType.SECP256K1 -> Secp256K1CryptoProvider
    else -> throw IllegalArgumentException("KeyPairType is not an account key pair: ${keyPairBytes.keyPairType}")
  }

  return AccountKeyPair(
    keyPairBytes.publicKeyBytes.toPublicKeyBytes(),
    keyPairBytes.privateKeyBytes.toPrivateKeyBytes(),
    cryptoProvider,
  )
}

fun <V> prefixKeysInMap(map: Map<String, V>, prefix: String): Map<String, V> {
  return map.mapKeysTo(mutableMapOf()) { "$prefix.${it.key}" }.toMap()
}

fun <T> mapNullableToList(value: T?): List<T> {
  return value?.let { listOf(it) } ?: emptyList()
}

fun base64UrlEncode(bytes: ByteArray): String {
  return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

fun fromBase64Url(string: String): ByteArray {
  return Base64.getUrlDecoder().decode(string)
}

fun toIso8601Format(
  zonedDateTime: ZonedDateTime,
  isoDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)
): String {
  return isoDateFormatter.format(zonedDateTime)
}

val URI.origin: String
  get() = "${this.scheme}://${this.authority}"

val URI.hostIsLocalhost
  get() = this.host == "localhost"

val URI.hostIsLoopbackAddress: Boolean
  get() {
    if (this.host == null) throw IllegalArgumentException("'host' must not be null")
    return when (this.scheme) {
      "http", "https" -> InetAddress.getByName(this.host).isLoopbackAddress
      else -> false
    }
  }

suspend fun<T> time(log: Logger, logLevel: Level, name: String, block: suspend () -> T): T {
  val start = System.currentTimeMillis()
  val result = block()
  val total = System.currentTimeMillis() - start
  log.atLevel(logLevel).log(
    "TIMING: {} {}",
    keyValue("functionName", name),
    keyValue("executionMillis", total)
  )
  return result
}

fun <T> toHeterogeneousVec(list: List<T>): HeterogeneousVecWriter.HeterogeneousVec<T> {
  val vec: HeterogeneousVecWriter.HeterogeneousVec<T> = HeterogeneousVecWriter.HeterogeneousVec()
  vec.addAll(list)
  return vec
}

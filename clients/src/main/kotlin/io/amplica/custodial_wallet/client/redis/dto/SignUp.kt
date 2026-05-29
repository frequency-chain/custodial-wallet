package io.amplica.custodial_wallet.client.redis.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.amplica.custodial_wallet.util.decodeValueToBytes
import io.amplica.custodial_wallet.validator.EmailValidator
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairDto
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.SignatureBytes
import io.amplica.custodial_wallet.util.key_creation.SignatureKeyPairType
import java.math.BigInteger

data class Signature(
  @JsonProperty("algo") val algo: SignatureKeyPairType,
  @JsonProperty("encoding") val encoding: Encoding,
  @JsonProperty("encodedValue") val encodedValue: String
) {
  fun toSignatureBytes(): SignatureBytes {
    return decodeValueToBytes(encodedValue, encoding)
  }
}

data class GenericPayloadRequest<T>(
  val payloadRequest: T
)

data class AddGraphKeyResponse(
  val signature: Signature,
  val payload: AddGraphKeyPayloadResponse
)

data class AddGraphKeyPayloadResponse(
  val schemaId: Int,
  val targetHash: Long,
  val expiration: Long,
  val publicKey: PublicKeyDto,
  val keyPair: KeyPairDto?
)

data class HandleRequest(
  val signature: Signature,
  val payload: HandlePayloadRequest
)

data class HandleResponse(
  override val signature: Signature,
  val payload: HandlePayloadResponse,
) : WithSignature

enum class UserIdentifierType {
  EMAIL,
  PHONE_NUMBER;

  companion object {
    fun fromString(value: String): UserIdentifierType {
      return if(EmailValidator.isValid(value)){
        EMAIL
      }else{
        PHONE_NUMBER
      }
    }
  }
}

open class UserIdentifier(
  @JsonProperty("value") val value: String,
  @JsonProperty("type") val type: UserIdentifierType
) {
  override fun toString(): String {
    return "UserIdentifier(value='$value', type=$type"
  }

  override fun equals(other: Any?): Boolean {
    return when (other) {
      is UserIdentifier -> {
        other.value == value && other.type == type
      }
      else -> false
    }
  }

  override fun hashCode(): Int {
    var result = value.hashCode()
    result = 31 * result + type.hashCode()
    return result
  }

}

class UserIdentifierResponse(value: String, type: UserIdentifierType) : UserIdentifier(value, type)

open class SignUpRequest(
  val externalUserId: String,
  val userIdentifiers: List<UserIdentifier>,
  val publicKey: PublicKeyDto,
  val signature: Signature,
  val payload: AddProviderPayloadRequest,
  val handle: HandleRequest,
  @JsonIgnore val token: String? = null
)

data class SignUpResponse(
  val externalUserId: String,
  val userIdentifiers: List<UserIdentifierResponse>,
  val token: String,
  val publicKey: PublicKeyDto,
  val addProviderPayloadSignature: Signature,
  val addProviderPayload: AddProviderPayloadResponse,
  val handle: HandleResponse,
  val graphKey: AddGraphKeyResponse?,
)

data class GenerateSmsCodePayload(
  val sessionId: String
)

data class AuthorizationCodeRequest(
  val authorizationCode: String,
  val sessionId: String
)

open class ProviderUserIdentifier(
  val providerMsaId: BigInteger,
  val userIdentifier: UserIdentifier
) {
  override fun toString(): String {
    return "ProviderUserIdentifier(providerMsaId='$providerMsaId', userIdentifier=$userIdentifier)"
  }
}
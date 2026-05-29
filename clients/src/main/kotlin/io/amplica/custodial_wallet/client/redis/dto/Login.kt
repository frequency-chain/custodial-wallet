package io.amplica.custodial_wallet.client.redis.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import java.net.URI

data class LoginPayload(
  val nonce: String,
  val url: URI?
)

data class LoginRequest(
  val userPublicKey: PublicKeyDto?,
  val externalUserId: String?,
  val userIdentifier: UserIdentifier,
  val publicKey: PublicKeyDto,
  val signature: Signature,
  val payload: LoginPayload,
  @JsonIgnore val token: String? = null
)

data class LoginResponse(
  val userPublicKey: PublicKeyDto?,
  val externalUserId: String?,
  val userIdentifier: UserIdentifier,
  val token: String,
  val publicKey: PublicKeyDto,
  val loginPayloadSignature: Signature,
  val loginPayload: LoginPayload
)

data class DirectLoginRequest(
  val contactMethod: String,
  val contactMethodType: UserIdentifierType,
  val callbackUrl: String?,
  val captchaToken: String? = null,
)

data class PasswordDirectLoginRequest(
  val username: String,
  val password: String,
  val callbackUrl: String?
)

enum class UserState {
  LOGGED_IN,
  LOGGED_OUT
}
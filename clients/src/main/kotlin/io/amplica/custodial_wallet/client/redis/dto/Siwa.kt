package io.amplica.custodial_wallet.client.redis.dto

import com.fasterxml.jackson.annotation.*
import com.google.common.collect.FluentIterable
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import org.springframework.web.bind.annotation.BindParam
import java.net.URI


data class SiwaRequest(
  @JsonProperty("requestedSignatures") val signatureRequest: SignedSiwaSignatureRequest,
  val requestedCredentials: List<RequestedCredential>,
  val siwaEmailHandling: SiwaEmailHandling? = null,
  val applicationContext: ApplicationContext? = null
)

data class ApplicationContext(
  val url: URI
)

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(
  JsonSubTypes.Type(RequestedCredential.AnyOf::class),
  JsonSubTypes.Type(RequestedCredential.SpecificCredential::class),
)
sealed interface RequestedCredential {

  data class AnyOf(
    val anyOf: List<SpecificCredential>
  ) : RequestedCredential

  data class SpecificCredential(
    val type: RequestedCredentialType,
    val hash: List<String>
  ) : RequestedCredential
  
}

enum class RequestedCredentialType(@JsonValue val value: String) {
  VerifiedEmailAddressCredential("VerifiedEmailAddressCredential"),
  VerifiedGraphKeyCredential("VerifiedGraphKeyCredential"),
  VerifiedPhoneNumberCredential("VerifiedPhoneNumberCredential"),
}

data class SignedSiwaSignatureRequest(
  val publicKey: PublicKeyDto,
  val signature: Signature, // Signature of the `payload`
  val payload: SiwaSignatureRequest
)

data class SiwaSignatureRequest(
  val callback: String,
  val permissions: List<Int>, // List of schema IDs
  val userIdentifierAdminUrl: String?,
)

data class SiwaSmsCode(val smsCode: String,)

data class SiwaIdentifierAndCaptchaToken(
  val value: String,
  val type: UserIdentifierType,
  @BindParam("h-captcha-response") val captchaToken: String?
){
  fun withValue(value: String): SiwaIdentifierAndCaptchaToken {
    return SiwaIdentifierAndCaptchaToken(value, type, captchaToken)
  }
}

enum class SiwaEmailHandling(@JsonValue val value: String, val description: String) {
  OTP("OTP", "Used to tell the application that it should use email templates that have the OTP(Token) in the body of the email and it will be entered manually, much like SMS"),
  MAGIC_LINK("MAGIC_LINK", "Used to tell the application that it should use email templates that have the OTP(Token) embedded in a link so a user just clicks the link"),
  ;

  companion object {
    @Suppress("UnstableApiUsage") //from is marked @Beta but its been beta for years
    private val VALUE_INDEX = FluentIterable.from(entries.toTypedArray()).uniqueIndex { it.value }

    @JsonCreator
    fun fromValue(value: String): SiwaEmailHandling {
      val retVal = VALUE_INDEX[value.uppercase()] ?: throw IllegalArgumentException("value=$value is not recognized")

      return retVal
    }
  }
}
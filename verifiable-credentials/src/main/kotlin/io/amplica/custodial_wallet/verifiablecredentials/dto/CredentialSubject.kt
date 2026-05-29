package io.amplica.custodial_wallet.verifiablecredentials.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonValue
import java.time.ZonedDateTime


@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(
  JsonSubTypes.Type(CredentialSubject.Email::class),
  JsonSubTypes.Type(CredentialSubject.PhoneNumber::class),
  JsonSubTypes.Type(CredentialSubject.KeyPair::class),
)
sealed interface CredentialSubject {

  data class Email(
    val id: String,
    val emailAddress: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    val lastVerified: ZonedDateTime
  ): CredentialSubject

  data class KeyPair(
    val id: String,
    val encodedPublicKeyValue: String,
    val encodedPrivateKeyValue: String,
    val encoding: KeyPairEncoding,
    val format: KeyPairFormat,
    val type: KeyPairType,
    val keyType: DsnpKeyType,
  ): CredentialSubject

  data class PhoneNumber(
    val id: String,
    val phoneNumber: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    val lastVerified: ZonedDateTime
  ): CredentialSubject

}

enum class KeyPairEncoding(@JsonValue val value: String) {
  BASE_16("base16")
}

enum class KeyPairType(@JsonValue val value: String) {
  X25519("X25519"),
}

enum class KeyPairFormat(@JsonValue val value: String) {
  BARE("bare")
}

enum class DsnpKeyType(@JsonValue val value: String) {
  PublicKeyKeyAgreement("dsnp.public-key-key-agreement"),
}

package io.amplica.custodial_wallet.client.redis.dto

import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto

data class IcsUserIdentifierRequestPayload (
  val userIdentifier: UserIdentifier,
  val nonce: String
)

data class IcsContextItemKeyRequestPayload (
  val msaId: String,
  val contextItemId: String,
  val nonce: String,
)

data class IcsMsaIdRequestPayload (
  val msaId: String,
  val nonce: String
)

data class IcsRetrievePayloadsSignedRequest(
  val publicKey: PublicKeyDto,
  val signature: Signature,
  val payload: IcsMsaIdRequestPayload
)

data class IcsRetrievePayloadsResponse(
  val userPublicKey: PublicKeyDto,
  val userIcsPublicKey: PublicKeyDto,
  val payloads: List<TypedPayloadResponseWithSignature<*>>,
)

data class IcsContextGroupKeySignedRequest(
  val publicKey: PublicKeyDto,
  val signature: Signature,
  val payload: IcsMsaIdRequestPayload
)

data class IcsContextItemKeySignedRequest(
  val publicKey: PublicKeyDto,
  val signature: Signature,
  val payload: IcsContextItemKeyRequestPayload
)

data class IcsSymmetricKey(
  val encodedKeyValue: String,
  val encoding: Encoding,
  val derivationPath: String,
)

data class IcsContextGroupKeyResponse(
  val contextGroupSymmetricKey: IcsSymmetricKey,
)

data class IcsContextItemKeyResponse(
  val contextItemSymmetricKey: IcsSymmetricKey
)

package io.amplica.custodial_wallet.util

import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.dto.SiwaPayloadResponse
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.PublicKeyFormat
import io.amplica.frequency.client.*
import io.amplica.frequency.client.DelegationGranted
import io.amplica.frequency.client.pallet.statefulstorage.AddItemAction
import io.amplica.frequency.client.pallet.statefulstorage.ItemAction
import io.amplica.frequency.signing_service.AddProviderPayload
import io.amplica.frequency.signing_service.HandlePayload
import io.amplica.frequency.signing_service.ItemizedSignaturePayloadV2
import io.amplica.frequency.util.arrow.getOrThrow
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.toPublicKeyBytes

fun generateUserIdentifier(): UserIdentifier {
  return UserIdentifier(generateUniqueEmail(), UserIdentifierType.EMAIL)
}

fun createUser(
  frequencyClient: FrequencyClient,
  userPublicKey: PublicKeyDto,
  signedAddProviderPayload: TypedPayloadResponseWithSignature<AddProviderPayloadResponse>
): DelegationGranted {
  val signature = signedAddProviderPayload.signature
  val addProviderPayloadResponse = signedAddProviderPayload.payload
  return frequencyClient.createSponsoredAccountWithDelegationWithCapacity(
    userPublicKey.toPublicKeyBytes(),
    SpRuntimeMultiSignatureType.SR25519,
    decodeValueToBytes(signature.encodedValue, signature.encoding),
    AddProviderPayload(
      addProviderPayloadResponse.authorizedMsaId,
      addProviderPayloadResponse.schemaIds,
      addProviderPayloadResponse.expiration
    )
  ).join().getOrThrow()
}

fun submitSiwaSignedPayloads(client: FrequencyClient, siwaPayloadResponse: SiwaPayloadResponse) {
  val publicKey = siwaPayloadResponse.userPublicKey
  val publicKeyBytes = when (publicKey.format) {
    PublicKeyFormat.SS58 -> base58DecodeAndExtractPublicKey(publicKey.encodedValue)
    PublicKeyFormat.BARE -> when (publicKey.type) {
      KeyPairType.SECP256K1 -> Secp256K1CryptoProvider.toUniversalAddress(
        decodeValueToBytes(publicKey.encodedValue, publicKey.encoding).toPublicKeyBytes()
      )
      else -> throw UnsupportedOperationException("Unsupported public key type: ${publicKey.type}")
    }
    else -> throw UnsupportedOperationException("Serialization to bytes has not been implemented for ${publicKey.format}")
  }

  val signatureType = when (publicKey.type) {
    KeyPairType.SR25519 -> SpRuntimeMultiSignatureType.SR25519
    KeyPairType.SECP256K1 -> SpRuntimeMultiSignatureType.ECDSA
    else -> throw UnsupportedOperationException("Signature type not defined for${publicKey.type} ")
  }

  val sortedPayloads = siwaPayloadResponse.payloads.sortedBy { when (it.payload) {
    // Add provider payload must be submitted before any other payloads
    is AddProviderPayloadResponse -> 0
    is ItemizedSignaturePayloadResponse -> 1
    is HandlePayloadResponse -> 1
    else -> throw IllegalArgumentException("Unable to assign an ordering to: ${it.payload::class.java}")
  } }

  sortedPayloads.forEach { signedPayload ->
    when (val payload = signedPayload.payload) {
      is AddProviderPayloadResponse -> {
        // Select the appropriate extrinsic to call based on the endpoint specified
        val executeExtrinsicCall = when (signedPayload.endpoint) {
          FrequencyEndpoint.Msa.grantDelegation -> client::grantDelegation
          else -> client::createSponsoredAccountWithDelegationWithCapacity
        }

        executeExtrinsicCall(
          publicKeyBytes,
          signatureType,
          fromHex(signedPayload.signature.encodedValue),
          AddProviderPayload(payload.authorizedMsaId, payload.schemaIds, payload.expiration)
        ).join().getOrThrow()
      }

      is HandlePayloadResponse -> client.claimHandleWithCapacity(
        publicKeyBytes,
        signatureType,
        fromHex(signedPayload.signature.encodedValue),
        HandlePayload(payload.baseHandle, payload.expiration)
      ).join().getOrThrow()

      is ItemizedSignaturePayloadResponse -> {
        val publicKeyHex = (payload.actions.first() as io.amplica.custodial_wallet.client.redis.dto.AddItemAction).payloadHex
        val actions = toHeterogeneousVec(listOf<ItemAction>(AddItemAction.from(fromHex(publicKeyHex))))
        val blockchainPayload = ItemizedSignaturePayloadV2(
          payload.schemaId,
          payload.targetHash.toBigInteger(),
          payload.expiration,
          actions
        )

         client.createApplyItemActionsWithSignatureV2(
          publicKeyBytes,
           signatureType,
           fromHex(signedPayload.signature.encodedValue),
          blockchainPayload
        ).join().getOrThrow()
      }

      else -> throw IllegalArgumentException("Unable to submit payload to chain: ${payload::class.java}")
    }
  }
}

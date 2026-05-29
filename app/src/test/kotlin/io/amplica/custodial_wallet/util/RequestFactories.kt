package io.amplica.custodial_wallet.util

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.siggen.SignatureGenerator
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider

fun convertToPublicKeyDto(providerKeyPair: SubstrateOrAccountKeyPair,): PublicKeyDto {
  return when (providerKeyPair) {
    is SubstrateOrAccountKeyPair.SubstrateKeyPairWrapper -> {
      Sr25519KeyPairCreator.createSr25519PublicKeyDto(
        Sr25519KeyPairBytes(
          providerKeyPair.keyPair.asPublicKey().bytes,
          providerKeyPair.keyPair.asSecretKey().bytes,
          KeyPairSignatureAlgorithm.SR25519
        ),
        SS58AddressFormat.SUBSTRATE_ACCOUNT
      )
    }

    is SubstrateOrAccountKeyPair.AccountKeyPairWrapper ->
      when (providerKeyPair.keyPair.cryptoProvider) {
        Sr25519CryptoProvider ->
          Sr25519KeyPairBytes(
            providerKeyPair.keyPair.publicKeyBytes.bytes,
            providerKeyPair.keyPair.privateKeyBytes.bytes,
            KeyPairSignatureAlgorithm.SR25519
          ).toPublicKeyDto(SS58AddressFormat.SUBSTRATE_ACCOUNT)

        Secp256K1CryptoProvider ->
          Secp256k1KeyPairBytes(
            providerKeyPair.keyPair.publicKeyBytes.bytes,
            providerKeyPair.keyPair.privateKeyBytes.bytes,
          ).toPublicKeyDto(SS58AddressFormat.SUBSTRATE_ACCOUNT)
      }
  }
}

fun siwaRequest(
  providerKeyPair: SubstrateOrAccountKeyPair,
  callbackUrl: String,
  permissions: List<Int>,
  userIdentifierAdminUrl: String?,
  siwaEmailHandling: SiwaEmailHandling?,
  applicationContext: ApplicationContext?,
): SiwaRequest {
  val signatureRequest = SiwaSignatureRequest(callbackUrl, permissions, userIdentifierAdminUrl)

  val providerPublicKeyDto: PublicKeyDto = convertToPublicKeyDto(providerKeyPair)

  val signedSiwaSignatureRequest = SignedSiwaSignatureRequest(
    providerPublicKeyDto,
    SignatureGenerator.signSiwaPayloadRequest(providerKeyPair, signatureRequest),
    signatureRequest,
  )

  return SiwaRequest(
    signedSiwaSignatureRequest,
    listOf(
      RequestedCredential.AnyOf(
        listOf(
          RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedEmailAddressCredential, listOf("???")),
          RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedPhoneNumberCredential, listOf("???"))
        )
      )
    ),
    siwaEmailHandling,
    applicationContext,
  )
}

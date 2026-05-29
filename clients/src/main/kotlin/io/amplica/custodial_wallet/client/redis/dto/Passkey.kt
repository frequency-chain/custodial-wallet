package io.amplica.custodial_wallet.client.redis.dto

import io.amplica.custodial_wallet.util.key_creation.EncodedBytes
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto

data class AcceptRegistrationRequest(
    val userHandle: String,
    val clientDataJSON: EncodedBytes,
    val attestationObject: EncodedBytes,
    val clientExtensions: String,
    val transports: Set<String>,
    val challenge: EncodedBytes,
    val credentialId: String,
    val passkeyCompressedPublicKey: PublicKeyDto,
    val accountPublicKey: PublicKeyDto,
    val credentialPublicKeySignature: Signature, // The compressed credential (P256) public key signed by the wallet account private key (SR25519)
    val credentialSignatureOfAccountPublicKey: Signature? // The wallet account public key (SR25519) signed by the credential (P256) private key
)

data class CredentialResponseDto(
    val credentialId: String,
    val passkeyCompressedPublicKey: PublicKeyDto,
    val accountPublicKey: PublicKeyDto,
    val credentialPublicKeySignature: Signature,
    val credentialSignatureOfAccountPublicKey: Signature?
)

data class CredentialResponsesDto(
    val credentials: List<CredentialResponseDto>
)
package io.amplica.custodial_wallet.service.verifiable_credential

import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.util.key_creation.KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.X25519KeyPair
import io.amplica.custodial_wallet.verifiablecredentials.dto.VerifiableCredential
import java.time.ZonedDateTime

interface VerifiableCredentialService {
  fun getIssuerDidJson(): String
  fun createVerifiableCredential(accountKeyPair: KeyPairBytes, graphKeyPair: X25519KeyPair): VerifiableCredential
  fun createVerifiableCredential(
    accountKeyPair: KeyPairBytes,
    userIdentifier: UserIdentifier,
    lastVerified: ZonedDateTime
  ): VerifiableCredential
}
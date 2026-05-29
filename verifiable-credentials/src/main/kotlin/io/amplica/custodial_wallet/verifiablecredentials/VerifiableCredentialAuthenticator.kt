package io.amplica.custodial_wallet.verifiablecredentials

import io.amplica.custodial_wallet.verifiablecredentials.cryptosuite.CryptoSuite
import io.amplica.custodial_wallet.verifiablecredentials.dto.Credential
import io.amplica.custodial_wallet.verifiablecredentials.dto.Proof
import io.amplica.custodial_wallet.verifiablecredentials.dto.ProofOptions
import io.amplica.custodial_wallet.verifiablecredentials.dto.VerifiableCredential


/**
 * A mechanism for signing and verifying credentials based on the
 * [W3C Verified Credentials Spec](https://www.w3.org/TR/vc-data-model-2.0/).
 *
 * By default, supports the `eddsa-rdfc-2022` cryptosuite.
 */
class VerifiableCredentialAuthenticator(cryptoSuites: List<CryptoSuite>) {

  private val cryptoSuiteByName = cryptoSuites.associateBy { it.name }

  private fun getCryptoSuiteOrThrow(name: String): CryptoSuite {
    return cryptoSuiteByName[name] ?: throw IllegalArgumentException(name)
  }

  fun sign(
    options: ProofOptions,
    credential: Credential,
    privateKey: ByteArray,
  ): VerifiableCredential {
    val cryptoSuite = getCryptoSuiteOrThrow(options.cryptosuite)
    val proofValue = cryptoSuite.sign(options, credential, privateKey)

    return VerifiableCredential.of(
      credential,
      Proof.of(options, proofValue)
    )
  }

  fun verify(verifiableCredential: VerifiableCredential, publicKey: ByteArray): Boolean {
    val options = verifiableCredential.proof.asOptions()
    val cryptoSuite = getCryptoSuiteOrThrow(options.cryptosuite)

    return cryptoSuite.verify(verifiableCredential, publicKey)
  }

}

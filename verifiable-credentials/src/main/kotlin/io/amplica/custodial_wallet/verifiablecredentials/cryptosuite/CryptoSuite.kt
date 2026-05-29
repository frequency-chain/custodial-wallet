package io.amplica.custodial_wallet.verifiablecredentials.cryptosuite

import io.amplica.custodial_wallet.verifiablecredentials.dto.Credential
import io.amplica.custodial_wallet.verifiablecredentials.dto.ProofOptions
import io.amplica.custodial_wallet.verifiablecredentials.dto.VerifiableCredential


interface CryptoSuite {
  val name: String

  fun sign(options: ProofOptions, credential: Credential, privateKey: ByteArray): String
  fun verify(verifiableCredential: VerifiableCredential, publicKey: ByteArray, ): Boolean
}

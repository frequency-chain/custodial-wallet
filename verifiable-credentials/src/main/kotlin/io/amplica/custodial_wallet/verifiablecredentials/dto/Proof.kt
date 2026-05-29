package io.amplica.custodial_wallet.verifiablecredentials.dto

import com.fasterxml.jackson.annotation.JsonValue


data class ProofOptions(
  val type: ProofType,
  val verificationMethod: String,
  val cryptosuite: String,
  val proofPurpose: ProofPurpose,
)

data class Proof(
  val type: ProofType,
  val verificationMethod: String,
  val cryptosuite: String,
  val proofPurpose: ProofPurpose,
  val proofValue: String
) {
  companion object {
    fun of(options: ProofOptions, value: String): Proof {
      return Proof(
        options.type,
        options.verificationMethod,
        options.cryptosuite,
        options.proofPurpose,
        value
      )
    }
  }

  fun asOptions() = ProofOptions(
      this.type,
      this.verificationMethod,
      this.cryptosuite,
      this.proofPurpose,
    )
}

enum class ProofType(@JsonValue val value: String) {
  DataIntegrityProof("DataIntegrityProof"),
}

enum class ProofPurpose(@JsonValue val value: String) {
  AssertionMethod("assertionMethod"),
}

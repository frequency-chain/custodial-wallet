package io.amplica.custodial_wallet.verifiablecredentials.canonicalization

import io.amplica.custodial_wallet.verifiablecredentials.dto.Credential
import io.amplica.custodial_wallet.verifiablecredentials.dto.ProofOptions


/**
 * Implementers provide serialization of credential and proof documents into 'canonical forms' (i.e., unambiguous;
 * same between all implementations)
 */
interface Canonicalizer {
  fun serialize(options: ProofOptions): String
  fun serialize(credential: Credential): String
}

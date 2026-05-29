package io.amplica.custodial_wallet.db

import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairBytes
import java.math.BigInteger

interface AbstractAccountKeysAndExternalUserProps {
  val providerMsaId: BigInteger
  val externalIdentifier: String
  val accountKeyPair: Sr25519KeyPairBytes
  val graphKeyPair: Sr25519KeyPairBytes
}
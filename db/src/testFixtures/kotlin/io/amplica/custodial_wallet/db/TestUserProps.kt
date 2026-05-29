package io.amplica.custodial_wallet.db

import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairCreator
import java.math.BigInteger

data class TestUserProps(
  override val providerMsaId: BigInteger,
  override val externalIdentifier: String,
  val userDetail: UserDetail,
  override val accountKeyPair: Sr25519KeyPairBytes = Sr25519KeyPairCreator.createKeyPair(),
  override val graphKeyPair: Sr25519KeyPairBytes = Sr25519KeyPairCreator.createKeyPair(),
): AbstractAccountKeysAndExternalUserProps
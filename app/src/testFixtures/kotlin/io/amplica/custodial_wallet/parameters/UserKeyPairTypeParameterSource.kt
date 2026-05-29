package io.amplica.custodial_wallet.parameters

import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import java.util.stream.Stream

object UserKeyPairTypeParameterSource {

  @JvmStatic
  fun sr25519Only(): Stream<KeyPairType> {
    return Stream.of(
      KeyPairType.SR25519,
    )
  }

  @JvmStatic
  fun sr25519AndEthereum(): Stream<KeyPairType> {
    return Stream.of(
      KeyPairType.SR25519,
      KeyPairType.SECP256K1,
    )
  }
}

package io.amplica.custodial_wallet.parameters

import io.amplica.frequency.crypto.provider.CryptoProvider
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import java.util.stream.Stream

object CryptoProviderParameterSource {

  @JvmStatic
  fun srAndEthereum(): Stream<CryptoProvider> {
    return Stream.of(
      Sr25519CryptoProvider,
      Secp256K1CryptoProvider,
    )
  }
}
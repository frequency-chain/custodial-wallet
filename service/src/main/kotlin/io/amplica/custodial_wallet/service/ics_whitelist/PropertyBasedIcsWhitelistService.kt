package io.amplica.custodial_wallet.service.ics_whitelist

import java.math.BigInteger

class PropertyBasedIcsWhitelistService(private val providerMsaIdWhitelist: Set<BigInteger>): IcsWhitelistService {
  override fun providerIsWhitelisted(providerMsaId: BigInteger): Boolean {
    return providerMsaIdWhitelist.contains(providerMsaId)
  }
}

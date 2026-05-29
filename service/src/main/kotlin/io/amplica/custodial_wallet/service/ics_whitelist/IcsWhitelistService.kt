package io.amplica.custodial_wallet.service.ics_whitelist

import java.math.BigInteger

interface IcsWhitelistService {
  fun providerIsWhitelisted(providerMsaId: BigInteger): Boolean
}
package io.amplica.custodial_wallet.blocking

import java.math.BigInteger


interface BlockingStrategy {
  suspend fun checkOrThrow(providerMsaId: BigInteger, sessionId: String?)
}
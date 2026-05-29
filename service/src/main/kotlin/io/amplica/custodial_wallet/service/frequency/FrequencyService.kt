package io.amplica.custodial_wallet.service.frequency

import io.amplica.frequency.crypto.AccountKeyPair
import java.math.BigInteger

interface FrequencyService {
  suspend fun createUserAccount(userKeyPair: AccountKeyPair): Result<BigInteger>
  suspend fun claimHandle(userKeyPair: AccountKeyPair, baseHandle: String): Result<String>
}

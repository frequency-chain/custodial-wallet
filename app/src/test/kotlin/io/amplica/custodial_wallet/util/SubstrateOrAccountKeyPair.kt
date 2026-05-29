package io.amplica.custodial_wallet.util

import com.strategyobject.substrateclient.crypto.KeyPair
import io.amplica.frequency.crypto.AccountKeyPair

sealed interface SubstrateOrAccountKeyPair {
  data class SubstrateKeyPairWrapper(val keyPair: KeyPair): SubstrateOrAccountKeyPair
  data class AccountKeyPairWrapper(val keyPair: AccountKeyPair): SubstrateOrAccountKeyPair
}


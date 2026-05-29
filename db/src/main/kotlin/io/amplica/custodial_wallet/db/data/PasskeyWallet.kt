package io.amplica.custodial_wallet.db.data

import io.amplica.custodial_wallet.db.repository.Credential
import io.amplica.custodial_wallet.db.repository.Wallet

data class PasskeyWallet(val credential: Credential, val wallet: Wallet)

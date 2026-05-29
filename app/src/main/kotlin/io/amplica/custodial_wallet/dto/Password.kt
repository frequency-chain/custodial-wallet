package io.amplica.custodial_wallet.dto

import java.math.BigInteger

data class ChangePasswordRequest(val userAccountId: BigInteger, val newRawPassword: String)

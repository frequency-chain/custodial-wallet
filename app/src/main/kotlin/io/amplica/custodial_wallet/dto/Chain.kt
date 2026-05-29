package io.amplica.custodial_wallet.dto

import java.math.BigInteger

data class FinalizedHeadNumberResponse(val finalizedHeadNumber: BigInteger)

data class LatestBlockNumberResponse(val latestBlockNumber: BigInteger)
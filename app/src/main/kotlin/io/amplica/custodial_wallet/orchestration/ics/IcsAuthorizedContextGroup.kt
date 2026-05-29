package io.amplica.custodial_wallet.orchestration.ics

import io.amplica.custodial_wallet.type.IdentifiedValue
import java.math.BigInteger

data class IcsAuthorizedContextGroup(
  val contextGroupId: ByteArray,
  val userSeed: IdentifiedValue<BigInteger, ByteArray>,
  val contextItemSeed: IdentifiedValue<BigInteger, ByteArray>,
)

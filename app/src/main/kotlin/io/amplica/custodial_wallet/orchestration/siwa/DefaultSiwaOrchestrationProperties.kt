package io.amplica.custodial_wallet.orchestration.siwa

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.client.redis.dto.SiwaEmailHandling
import io.amplica.custodial_wallet.orchestration.SmsProperties
import io.amplica.custodial_wallet.web.Environment
import java.time.Duration


data class IdentifierVerificationProperties(
  val resendInterval: Duration,
  val resendLimit: Int,
  val resendFreebee: Int,
  val incorrectAttemptLimit: Int,
)

data class DefaultSiwaOrchestrationProperties(
  val authentication: IdentifierVerificationProperties,
  val schemaIdsToPermissionMessageKeys: Map<Set<Int>, String>,
  val signupBlockExpiration: Long,
  val sms: SmsProperties,
  val ss58AddressFormat: SS58AddressFormat,
  val redisExpiration: Duration,
  val defaultSiwaEmailHandling: SiwaEmailHandling,
  val passkeyActive: Boolean,
  val environment: Environment,
)

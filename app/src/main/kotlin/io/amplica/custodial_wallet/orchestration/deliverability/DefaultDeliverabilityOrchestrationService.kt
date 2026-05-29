package io.amplica.custodial_wallet.orchestration.deliverability

import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.notification.PhoneNumberBlockStatus
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.controller.util.NormalizationUtil
import io.amplica.custodial_wallet.validator.MEWE_TEST_PHONE_PREFIX
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DefaultDeliverabilityOrchestrationService(
  private val normalizationUtil: NormalizationUtil,
  private val notificationServiceClient: NotificationServiceClient,
) : DeliverabilityOrchestrationService {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(DefaultDeliverabilityOrchestrationService::class.java)
  }

  private fun isMeWeTestPhoneNumber(userIdentifier: UserIdentifier): Boolean {
    return userIdentifier.type == UserIdentifierType.PHONE_NUMBER
            && userIdentifier.value.startsWith(MEWE_TEST_PHONE_PREFIX)
  }

  override suspend fun getDeliverability(userIdentifier: UserIdentifier): Boolean {
    if (isMeWeTestPhoneNumber(userIdentifier)) {
      return true
    }

    // Check the validity of the identifier
    val normalizedIdentifier = normalizationUtil.normalizeUserIdentifier(userIdentifier)

    return when (normalizedIdentifier.type) {
      // NOTE(Julian, 2025-02-06): We will currently send an email to any valid email address
      UserIdentifierType.EMAIL -> true

      UserIdentifierType.PHONE_NUMBER -> {
        val status = notificationServiceClient.lookupPhoneNumber(normalizedIdentifier.value).blockStatus

        return when (status) {
          is PhoneNumberBlockStatus.Blocked -> {
            LOG.info("Phone number '${normalizedIdentifier.value}' is blocked due to reason: ${status.reason}")
            false
          }

          else -> true
        }
      }
    }
  }

}
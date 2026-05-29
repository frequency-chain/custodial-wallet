package io.amplica.custodial_wallet.orchestration.deliverability

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.notification.PhoneNumberBlockReason
import io.amplica.custodial_wallet.client.notification.PhoneNumberBlockStatus
import io.amplica.custodial_wallet.client.notification.PhoneNumberLookupResponse
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.controller.util.NormalizationUtil
import io.amplica.custodial_wallet.exception.ApiException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DefaultDeliverabilityOrchestrationServiceTest {

  @ParameterizedTest
  @CsvSource(value = [
    "EMAIL, fang.runin@sinegard.edu.nk, , true",
    "PHONE_NUMBER, +89111222333, true, true", // 'MeWe' country code is always treated as deliverable
    "PHONE_NUMBER, +15415446789, false, true",
    "PHONE_NUMBER, +15415446789, true, false",
  ])
  fun getDeliverability(type: UserIdentifierType, value: String, phoneIsBlocked: Boolean?, expectedResponse: Boolean) {
    // GIVEN
    val identifier = UserIdentifier(value, type)

    val normalizationUtil = NormalizationUtil(PhoneNumberUtil.getInstance())
    val mockNotificationServiceClient: NotificationServiceClient = mock()
    val service = DefaultDeliverabilityOrchestrationService(normalizationUtil, mockNotificationServiceClient)

    if (phoneIsBlocked != null) {
      runBlocking {
        val status = when {
          phoneIsBlocked -> PhoneNumberBlockStatus.Blocked(PhoneNumberBlockReason.UNREACHABLE)
          else -> PhoneNumberBlockStatus.NotBlocked
        }
        whenever(mockNotificationServiceClient.lookupPhoneNumber(eq(value)))
          .thenReturn(PhoneNumberLookupResponse(status))
      }
    }

    // WHEN
    val response = runBlocking {
      service.getDeliverability(identifier)
    }

    // THEN
    Assertions.assertThat(response).isEqualTo(expectedResponse)
  }

  @ParameterizedTest
  @CsvSource(value = [
    "EMAIL, fang.runin.edu.nk",
    "PHONE_NUMBER, +15555",
  ])
  fun getDeliverabilityInvalidValue(type: UserIdentifierType, value: String) {
    // GIVEN
    val identifier = UserIdentifier(value, type)

    val normalizationUtil = NormalizationUtil(PhoneNumberUtil.getInstance())
    val mockNotificationServiceClient: NotificationServiceClient = mock()
    val service = DefaultDeliverabilityOrchestrationService(normalizationUtil, mockNotificationServiceClient)

    // WHEN / THEN
    val expectedErrorMessage = when (type) {
      UserIdentifierType.EMAIL -> "User entered an invalid email"
      UserIdentifierType.PHONE_NUMBER -> "Phone number is invalid"
    }
    Assertions.assertThatThrownBy {
      runBlocking {
        service.getDeliverability(identifier)
      }
    }.isInstanceOf(ApiException::class.java).hasMessageContaining(expectedErrorMessage)
  }

}
package io.amplica.custodial_wallet.controller.util

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NormalizationUtilTest {
  private lateinit var normalizationUtil: NormalizationUtil

  @BeforeEach
  fun setUp() {
    normalizationUtil = NormalizationUtil(PhoneNumberUtil.getInstance())
  }

  @Test
  fun normalizeUserIdentifierEmail() {
    //GIVEN
    val emailToNormalize = "   SomEEmail@example.com    "
    val expected = "someemail@example.com"
    val userIdentifier = UserIdentifier(emailToNormalize, UserIdentifierType.EMAIL)

    //WHEN
    val retVal = normalizationUtil.normalizeUserIdentifier(userIdentifier)

    //THEN
    Assertions.assertThat(retVal.value).isEqualTo(expected)
  }

  @Test
  fun normalizeUserIdentifierInvalidEmail() {
    val invalidEmail = "thisIsAnInvalidEmail"
    val expected = "thisisaninvalidemail"
    Assertions.assertThatThrownBy {
      normalizationUtil.normalizeContactMethod(invalidEmail, UserIdentifierType.EMAIL)
    }.isInstanceOf(ApiException::class.java)
      .hasMessage("User entered an invalid email $expected")
      .extracting {
        val apiException = it as ApiException
        Assertions.assertThat(apiException.apiError).isEqualTo(ApiError.INVALID_EMAIL)
      }
  }

  @Test
  fun normalizeUserIdentifierPhone() {
    //GIVEN
    val phoneNumberToNormalize = "+1(310)867-5309"
    val expected = "+13108675309"
    val userIdentifier = UserIdentifier(phoneNumberToNormalize, UserIdentifierType.PHONE_NUMBER)

    //WHEN
    val retVal = normalizationUtil.normalizeUserIdentifier(userIdentifier)

    //THEN
    Assertions.assertThat(retVal.value).isEqualTo(expected)
  }

  @Test
  fun normalizeContactMethodEmail() {
    //GIVEN
    val phoneNumberToNormalize = "+1(310)867-5309"
    val expected = "+13108675309"

    //WHEN
    val retVal = normalizationUtil.normalizeContactMethod(phoneNumberToNormalize, UserIdentifierType.PHONE_NUMBER)

    //THEN
    Assertions.assertThat(retVal).isEqualTo(expected)
  }

  @Test
  fun normalizeContactMethodNumberParseException() {
    //GIVEN
    val phoneNumberToNormalize = "3108675309"

    //WHEN THEN
    Assertions.assertThatThrownBy {
      normalizationUtil.normalizeContactMethod(phoneNumberToNormalize, UserIdentifierType.PHONE_NUMBER)
    }.isInstanceOf(ApiException::class.java)
      .hasRootCauseInstanceOf(NumberParseException::class.java)
      .extracting {
        val apiException = it as ApiException
        Assertions.assertThat(apiException.apiError).isEqualTo(ApiError.INVALID_COUNTRY_CODE)
      }
  }

  @ParameterizedTest
  @ValueSource(strings = ["developers+12343@unfinshed.com", "dev+database@projectliberty.io"])
  fun isPlusAddressed(email: String) {
    val isPlusAddressed = normalizationUtil.isPlusAddressed(email)
    Assertions.assertThat(isPlusAddressed).isTrue()
  }

  @ParameterizedTest
  @ValueSource(strings = ["developers-dfsfd@unfinshed.com", "developers@projectliberty.io", "not_plus&addressed@people.org"])
  fun isNotPlusAddressed(email: String) {
    val isPlusAddressed = normalizationUtil.isPlusAddressed(email)
    Assertions.assertThat(isPlusAddressed).isFalse()
  }

  @ParameterizedTest
  @ValueSource(strings = ["developers+12343@unfinished.com", "developers+database-people@unfinished.com", "developers+bob.34@unfinished.com", "developers@unfinished.com"])
  fun stripPlusAddressing(email: String) {
    val strippedEmail = normalizationUtil.stripPlusAddressing(email)
    Assertions.assertThat(strippedEmail).isEqualTo("developers@unfinished.com")
  }
}
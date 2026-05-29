package io.amplica.custodial_wallet.controller.util

import org.assertj.core.api.Assertions
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.validator.EmailValidator
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EmailValidatorTest {
  @Test
  fun validEmail() {
    EmailValidator.validate("peter.frank@unfinished.com")
  }

  @ParameterizedTest
  @ValueSource(strings = ["peter.frank",
    "peter frank@unfinished.com",
    "peter.frank@unfinish .com",
    "peter.frank@unfinished",
    "\"peter.frank@unfinished.com"])
  fun invalidEmail(email: String) {
    Assertions.assertThatThrownBy { EmailValidator.validate(email) }
      .isInstanceOf(ApiException::class.java)
      .extracting {
        val x = it as ApiException
        x.apiError
      }
      .isEqualTo(ApiError.INVALID_EMAIL)
  }
}
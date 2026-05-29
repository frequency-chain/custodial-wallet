package io.amplica.custodial_wallet.validator

import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import org.slf4j.LoggerFactory


const val MEWE_TEST_PHONE_PREFIX = "+89"

object EmailValidator {
  private val EMAIL_REGEX = Regex(
    "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
        "\\@" +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
        "(" +
        "\\." +
        "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
        ")+"
  ) //Stolen from https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/util/Patterns.java#435

  fun validate(email: String) {
    if (!isValid(email)) {
      throw ApiException(ApiError.INVALID_EMAIL, "Email address=${email} isn't valid")
    }
  }

  fun isValid(email: String): Boolean {
    return EMAIL_REGEX.matches(email)
  }
}

class PhoneNumberValidator(private val phoneNumberUtil: PhoneNumberUtil) {
  companion object {
    private val LOG = LoggerFactory.getLogger(PhoneNumberValidator::class.java)
  }

  fun validate(phoneNumber: String) {
    if (!isValid(phoneNumber)) {
      throw ApiException(ApiError.NOT_A_PHONE_NUMBER, "Phone number=${phoneNumber} isn't valid")
    }
  }

  fun isValid(phoneNumber: String): Boolean {
    LOG.debug("The phone number to validate is {}", phoneNumber)
    //MeWe uses +89 as a testing region this is to handle that
    if (phoneNumber.startsWith(MEWE_TEST_PHONE_PREFIX)) {
      return true
    }

    return phoneNumberUtil.isValidNumber(phoneNumberUtil.parse(phoneNumber, ""))
  }
}

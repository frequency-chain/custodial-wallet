package io.amplica.custodial_wallet.controller.util

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.validator.EmailValidator
import java.util.*

fun coerceToApiError(numberParseException: NumberParseException, userIdentifierValue: String): Exception{
  return when (numberParseException.errorType) {
    NumberParseException.ErrorType.INVALID_COUNTRY_CODE -> ApiException(ApiError.INVALID_COUNTRY_CODE, "Phone number=${userIdentifierValue} contains an invalid country code.", numberParseException)
    NumberParseException.ErrorType.NOT_A_NUMBER -> ApiException(ApiError.NOT_A_PHONE_NUMBER, "Phone number=${userIdentifierValue} is invalid.", numberParseException)
    NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD -> ApiException(ApiError.PHONE_NUMBER_TOO_SHORT_AFTER_IDD, "Phone number=${userIdentifierValue} is too short after removing international dialing prefix.", numberParseException)
    NumberParseException.ErrorType.TOO_SHORT_NSN -> ApiException(ApiError.PHONE_NUMBER_TOO_SHORT_NSN, "Phone number=${userIdentifierValue} is too short after removing country code.", numberParseException)
    NumberParseException.ErrorType.TOO_LONG -> ApiException(ApiError.PHONE_NUMBER_TOO_LONG, "Phone number=${userIdentifierValue} is too long.", numberParseException)
    null -> IllegalStateException("ErrorType was null", numberParseException)
  }
}

class NormalizationUtil(private val phoneNumberUtil: PhoneNumberUtil) {
  fun normalizeUserIdentifier(userIdentifier: UserIdentifier): UserIdentifier {
    val userIdentifierType = userIdentifier.type
    val value = userIdentifier.value
    val normalizedValue = when(userIdentifierType) {
      UserIdentifierType.EMAIL -> {
        normalizeEmail(value)
      }
      UserIdentifierType.PHONE_NUMBER -> {
        normalizePhoneNumber(value)
      }
    }

    return UserIdentifier(normalizedValue, userIdentifierType)
  }

  fun normalizeContactMethod(value: String, type: UserIdentifierType): String {
    return normalizeUserIdentifier(UserIdentifier(value, type)).value
  }

  private fun normalizePhoneNumber(phoneNumber: String): String {
    try {
      val normalizedPhoneNumber = phoneNumberUtil.parse(phoneNumber, "")
      if(phoneNumberUtil.isValidNumber(normalizedPhoneNumber)) {
        return "+${normalizedPhoneNumber.countryCode}${normalizedPhoneNumber.nationalNumber}"
      } else {
        throw ApiException(ApiError.NOT_A_PHONE_NUMBER, "Phone number is invalid $normalizedPhoneNumber")
      }
    }catch(x: NumberParseException) {
      throw coerceToApiError(x, phoneNumber)
    }
  }

  private fun normalizeEmail(email: String): String {
    val normalizedEmail = email.trim().lowercase(Locale.US)
    if(EmailValidator.isValid(normalizedEmail)) {
      return normalizedEmail
    } else {
      throw ApiException(ApiError.INVALID_EMAIL, "User entered an invalid email $normalizedEmail")
    }
  }
  
  fun isPlusAddressed(email: String): Boolean {
    return email.contains("+")
  }

  fun stripPlusAddressing(email: String): String {
    val username = email.substringBefore("+", email.substringBeforeLast("@"))
    val domain = email.substringAfterLast("@")
    return "$username@$domain"
  }
}
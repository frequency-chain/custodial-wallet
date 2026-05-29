package io.amplica.custodial_wallet.exception

import com.google.common.collect.FluentIterable
import org.springframework.http.HttpStatus

data class ApiErrorDto(
  val id: Int,
  val description: String,
  val stackTrace: String?
)

enum class ApiError(val id: Int, val description: String, val httpStatus: HttpStatus){
  UNKNOWN_ERROR(0, "An Unknown Error has Occurred", HttpStatus.INTERNAL_SERVER_ERROR),
  NO_TOS_AGREEMENT_ERROR(1, "User has not agreed to Terms of Service", HttpStatus.EXPECTATION_FAILED),
  NO_SESSION_INFO_FOUND_ERROR(2,"No Session is associated with the given Session ID", HttpStatus.NOT_FOUND),
  NO_LOGIN_REQUEST_FOR_SESSION_ERROR(3,"No Login Request is associated with the given Session ID", HttpStatus.NOT_FOUND),
  NO_SIGNUP_REQUEST_FOR_SESSION_ERROR(4,"No SignUp Request is associated with the given Token or Session ID", HttpStatus.NOT_FOUND),
  NO_USER_FOUND_ERROR(5,"A user with this ID has not signed up yet", HttpStatus.NOT_FOUND),
  NULL_REQUIRED_FIELD_ERROR(6, "No value was passed for a required field", HttpStatus.UNPROCESSABLE_ENTITY),
  EMPTY_REQUIRED_LIST_ERROR(7, "No values were added to a list that requires at least one value", HttpStatus.UNPROCESSABLE_ENTITY),
  DATABASE_SAVE_ERROR(8, "Unable to save info to database", HttpStatus.INTERNAL_SERVER_ERROR),
  INVALID_SIGNATURE(9, "The signature isn't valid", HttpStatus.FORBIDDEN),
  MISSING_CALLBACK_URL(10, "No callback url provided in signup request for web browser", HttpStatus.UNPROCESSABLE_ENTITY),
  URL_NOT_ON_WHITELIST_ERROR(11, "The provided url is not whitelisted", HttpStatus.FORBIDDEN),
  MSA_ID_NOT_ON_WHITELIST_ERROR(12, "The provided msa id is not whitelisted", HttpStatus.FORBIDDEN),
  NO_MSA_ID_FOUND_ERROR(13, "No Msa Id is associated with the provided public key in the request", HttpStatus.NOT_FOUND),
  NO_BLOCK_NUMBER_FOUND_ERROR(14, "No block number was returned when requesting the current finalized block head number", HttpStatus.NOT_FOUND),
  MULTIPLE_IDENTITIES_DURING_SIGNUP_ERROR(15, "Multiple identities for user found during signup process", HttpStatus.UNPROCESSABLE_ENTITY),
  NO_PHONE_IDENTIFIER_IN_SMS_CALL(16, "No Phone type user identifier was found. This is required in an SMS call", HttpStatus.NOT_FOUND),
  TOO_MANY_RECORDS_REQUESTED(17, "Too many records were requested", HttpStatus.BAD_REQUEST),
  NO_LOGIN_REQUEST_FOR_TOKEN_ERROR(18, "No Login Request is associated with the given Token", HttpStatus.NOT_FOUND),
  NO_SIGNUP_REQUEST_FOR_TOKEN_ERROR(19, "No Signup Request found matching the given token", HttpStatus.NOT_FOUND),
  RESEND_LIMIT_EXCEEDED(20, "User has hit resend limit", HttpStatus.TOO_MANY_REQUESTS),
  RESEND_REQUEST_INVALID(21, "User has sent resend request before time limit", HttpStatus.TOO_EARLY),
  INVALID_EMAIL(22, "Invalid email address", HttpStatus.BAD_REQUEST),
  IDENTIFIER_ALREADY_USED(23, "This identifier is already in use", HttpStatus.CONFLICT),
  MSA_ID_MISMATCH_ERROR(24, "The retrieved msaId doesn't match the expected msaId", HttpStatus.FORBIDDEN),
  INVALID_SCHEMA_IDS(25, "Invalid schema IDs", HttpStatus.BAD_REQUEST),
  NOT_A_PHONE_NUMBER(26, "Invalid phone number", HttpStatus.BAD_REQUEST),
  PHONE_NUMBER_TOO_SHORT_AFTER_IDD(27, "Phone number too short after removing international dialing prefix", HttpStatus.BAD_REQUEST),
  PHONE_NUMBER_TOO_SHORT_NSN(28, "Phone number too short after removing country code", HttpStatus.BAD_REQUEST),
  PHONE_NUMBER_TOO_LONG(29, "Phone number too long", HttpStatus.BAD_REQUEST),
  INVALID_COUNTRY_CODE(30, "The phone number country code did not belong to a supported country or non-geographical entity", HttpStatus.BAD_REQUEST),
  NO_EMAIL_TEMPLATE_FOUND_ERROR(31, "No email template found", HttpStatus.INTERNAL_SERVER_ERROR),
  INVALID_CONTACT_METHOD_ERROR(32, "The contact method is invalid", HttpStatus.BAD_REQUEST),
  NO_WEBSITE_SESSION_FOUND_ERROR(33, "No Website Session found for given session ID", HttpStatus.NOT_FOUND),
  MISSING_AUTHENTICATION_CODE(34, "No authentication code given in authentication link", HttpStatus.BAD_REQUEST),
  MISSING_SESSION_ID(35, "No authentication code given in authentication link", HttpStatus.BAD_REQUEST),
  PROVIDER_NOT_FOUND(36, "No provider found for this msa id", HttpStatus.NOT_FOUND),
  PAYLOAD_TYPE_NOT_SUPPORTED(37, "This payload type is not supported", HttpStatus.UNPROCESSABLE_ENTITY),
  INVALID_PUBLIC_KEY_FORMAT(38, "Invalid public key format", HttpStatus.BAD_REQUEST),
  REVOKE_DELEGATION_FAILED(39, "Revoke delegation failed", HttpStatus.INTERNAL_SERVER_ERROR),
  PERMISSION_ERROR(40, "User does not have permission to perform this action", HttpStatus.FORBIDDEN),
  NO_PROVIDER_EXTERNAL_USER_ID_FOUND(41, "No Provider External User ID found", HttpStatus.NOT_FOUND),
  NO_USER_ACCOUNT_ID_FOUND(42, "No User Account Id found", HttpStatus.NOT_FOUND),
  NO_USER_IDENTIFIER_FOUND(43, "No User Identifier found", HttpStatus.NOT_FOUND),
  NO_PUBLIC_KEY_FOUND(44, "No Public Key found", HttpStatus.NOT_FOUND),
  NO_PROVIDER_EXTERNAL_USER_DETAIL_FOUND(45, "No Provider External User Detail found", HttpStatus.NOT_FOUND),
  RETIRE_MSA_FAILED(46, "Retire Msa failed", HttpStatus.INTERNAL_SERVER_ERROR),
  RETIRE_HANDLE_FAILED(47, "Retire handle failed", HttpStatus.INTERNAL_SERVER_ERROR),
  NO_USER_KEY_DATA_FOUND(48, "No user key data found", HttpStatus.NOT_FOUND),
  INVALID_HANDLE(49, "Invalid Handle", HttpStatus.BAD_REQUEST),
  NO_LOGGED_IN_SESSION_FOUND(50, "WebsiteSession found for SessionId has loggedIn=false", HttpStatus.NOT_FOUND),
  LOGIN_SESSION_DELETE_ERROR(51, "Login Website Session does not match deleted Website Session", HttpStatus.INTERNAL_SERVER_ERROR),
  INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED(52, "User has entered the wrong token too many times", HttpStatus.FORBIDDEN),
  HANDLE_UNAVAILABLE(53, "Handle has no suffixes available", HttpStatus.BAD_REQUEST),
  NO_WEBSITE_SESSION_FOR_TOKEN_ERROR(54, "No WebsiteSession found for given session ID and authentication/verification code", HttpStatus.NOT_FOUND),
  BLOCKED_CONTACT_METHOD(55, "We are unable to send to the provided contact method (email or phone number).", HttpStatus.BAD_REQUEST),
  NO_PAYLOAD_FOUND_ERROR(56,"No Payload is associated with the given Session ID", HttpStatus.NOT_FOUND),
  NO_BATCH_TO_SIGN_FOR_SESSION(57,"No Batch Payload to Sign is associated with the given Session ID", HttpStatus.NOT_FOUND),
  NO_PASSWORD_FOUND(58, "No password found", HttpStatus.NOT_FOUND),
  MULTIPLE_IDENTITIES_FOUND(59, "Multiple identities for user found", HttpStatus.UNPROCESSABLE_ENTITY),
  INCORRECT_PASSWORD(60, "Password entered by user is incorrect", HttpStatus.BAD_REQUEST),
  INVALID_SHARED_SECRET(61, "Rejected a send sms request with an invalid shared secret", HttpStatus.UNAUTHORIZED),
  UNKNOWN_NOTIFICATION_SERVICE_ERROR(62, "An unknown error occurred attempting to send notification", HttpStatus.INTERNAL_SERVER_ERROR),
  ERROR_SENDING_SMS(63, "Error attempting to send Sms", HttpStatus.INTERNAL_SERVER_ERROR),
  ALL_SMS_CLIENTS_FAILED(64, "All SMS clients failed", HttpStatus.INTERNAL_SERVER_ERROR),
  SIWA_SESSION_NOT_FOUND(65, "No SIWA session found for given session ID", HttpStatus.NOT_FOUND),
  SIWA_SESSION_NOT_FOUND_FOR_TOKEN(66, "No SIWA session found for given session ID and verification (or authentication) code", HttpStatus.NOT_FOUND),
  SIWA_INVALID_STATE(67, "The given SIWA request cannot be processed", HttpStatus.BAD_REQUEST),
  SIWA_INVALID_REQUEST(68, "The given SIWA request is not valid", HttpStatus.BAD_REQUEST),
  UNABLE_TO_PARSE_REGISTRATION_REQUEST(69, "Unable to Parse Registration Request", HttpStatus.INTERNAL_SERVER_ERROR),
  UNABLE_TO_VALIDATE_REGISTRATION_REQUEST(70, "Unable to Validate Registration Request", HttpStatus.INTERNAL_SERVER_ERROR),
  NO_TOKEN_FOUND_FOR_SESSION(71, "Unable to find a token associated with that session", HttpStatus.BAD_REQUEST),
  RESERVED_PARAMETER_VIOLATION(72, "Unable to process request since a reserved parameter has been detected", HttpStatus.BAD_REQUEST),
  TEMPORARILY_BANNED_PROVIDER(73, "This provider is temporarily unable to send notifications  Try again later", HttpStatus.FORBIDDEN),
  PROVIDER_METADATA_NOT_FOUND(74, "No provider metadata found for this msa id", HttpStatus.NOT_FOUND),
  HCAPTCHA_SITE_VERIFY_REQUEST_ERROR(75, "Error attempting to request site verification from hCaptcha", HttpStatus.INTERNAL_SERVER_ERROR),
  CAPTCHA_REQUIREMENT_NOT_SATISFIED(76, "No captcha token value present when required", HttpStatus.INTERNAL_SERVER_ERROR),
  BAD_CAPTCHA_ATTEMPT(77, "Siwa attempt flagged by captcha service as bad, likely a bot", HttpStatus.BAD_REQUEST),
  RESEND_CAPTCHA_ATTEMPT(78, "Siwa attempt flagged by captcha service as suspicious, need to redo captcha", HttpStatus.BAD_REQUEST),
  ORGANIZATION_NOT_FOUND(79, "No organization found for the given id", HttpStatus.NOT_FOUND),
  PASSKEY_WALLET_NOT_FOUND_FOR_THIS_USER(80, "A passkey wallet associated with this user was not found", HttpStatus.NOT_FOUND),
  PASSKEY_WALLET_NOT_FOUND_FOR_THIS_DEVICE(81, "A passkey wallet exists with this user but was not found for this device", HttpStatus.NOT_FOUND),
  PASSKEY_WALLET_CREDENTIAL_ID_DOES_NOT_MATCH_USER_ACCOUNT_ID(82, "A passkey wallet with the given credential id is not associated with this user", HttpStatus.BAD_REQUEST),
  PASSKEY_WALLET_ALREADY_EXISTS_FOR_THIS_USER_ACCOUNT(83, "A passkey wallet associated with this user account already exists and a new one cannot be created", HttpStatus.CONFLICT),
  SIGNATURE_TYPE_NOT_SUPPORTED(84, "This signature type is not supported", HttpStatus.UNPROCESSABLE_ENTITY),
  AUTHENTICATED_SIWA_SESSION_MISSING_AUTHORIZATION_CODE(85, "Authenticated siwa session did not have a authorization code set.", HttpStatus.INTERNAL_SERVER_ERROR),
  INVALID_REQUEST(86, "The request could not be processed", HttpStatus.BAD_REQUEST),
  NO_DELEGATIONS_EXIST(87, "Cannot revoke delegations when no delegations exist for this delegator and provider", HttpStatus.INTERNAL_SERVER_ERROR),
  NO_PROVIDER_FREQUENCY_ACCOUNT_FOUND(88, "No provider frequency account found for the given id", HttpStatus.NOT_FOUND),
  BLOCKED_PHONE_NUMBER(89, "We are unable to send to the provided phone number", HttpStatus.BAD_REQUEST),
  SIWA_SESSION_UNAUTHENTICATED(90, "The SIWA session must be authenticated to perform this action", HttpStatus.FORBIDDEN),
  NO_DB_USER_IDENTIFIER_FOUND(91, "No Db User Identifier found", HttpStatus.NOT_FOUND),
  NO_PROVIDER_APPLICATION_FOUND(92, "No Provider Application found", HttpStatus.NOT_FOUND),
  MSA_DOES_NOT_MATCH_PROVIDER_APPLICATION(93, "Provider Msa Id associated with Provider Frequency account does not match the account id attached to the ProviderApplication Id supplied.", HttpStatus.CONFLICT),
  CLAIM_SERVICE_USER_AGREE_ERROR(94, "Claim Service User Agree Error", HttpStatus.INTERNAL_SERVER_ERROR),
  CLAIM_SERVICE_GET_NONCE_ERROR(95, "Claim Service Get Nonce Error", HttpStatus.INTERNAL_SERVER_ERROR),
  NO_WALLET_METADATA_FOUND_FOR_PASSKEY_WALLET_CREDENTIAL(96, "No Passkey Wallet Metadata found for given credential", HttpStatus.NOT_FOUND),
  PASSKEY_WALLET_EXISTS_WITHOUT_ACCOUNT_PUBLIC_KEY(97, "User has a passkey wallet without an account public key", HttpStatus.INTERNAL_SERVER_ERROR),
  PASSKEY_WALLET_WITH_NON_UNIQUE_ACCOUNT_PUBLIC_KEY(98, "User has multiple passkey wallets, but wallets contain different account public keys", HttpStatus.INTERNAL_SERVER_ERROR),
  MULTIPLE_USER_ACCOUNT_IDS_IN_WEBSITE_SESSION_FOUND(99, "Multiple user account ids found in website session", HttpStatus.INTERNAL_SERVER_ERROR),
  CHANGE_HANDLE_FAILED(100, "Change handle failed", HttpStatus.INTERNAL_SERVER_ERROR),
  RATE_LIMIT_EXCEEDED(101, "The operation has been requested too many times", HttpStatus.TOO_MANY_REQUESTS),
  CHANGE_HANDLE_TOO_SOON(102, "Change handle requested too soon", HttpStatus.TOO_MANY_REQUESTS),
  BLOCKCHAIN_EXTRINSIC_ERROR(103, "Submitting an extrinsic failed or timed out", HttpStatus.INTERNAL_SERVER_ERROR),
  ICS_INVALID_STATE_ERROR(104, "The given ICS request cannot be processed", HttpStatus.BAD_REQUEST),
  ICS_ACL_CHECK_ERROR(105, "Unable to find a valid Context Group ACL", HttpStatus.FORBIDDEN),
  ICS_CONTEXT_ITEM_KEY_RESUBMISSION(106, "Cannot retrieve a Context Item Key for that Context Item Id", HttpStatus.FORBIDDEN),
  NO_ASYNC_SUBMISSION_FOUND(107, "No async submission found for given ID", HttpStatus.NOT_FOUND),
  ;

  companion object {
    @Suppress("UnstableApiUsage") // `from` is marked @Beta, but it's been beta for years
    private val API_ERROR_INDEX: Map<Int, ApiError> = FluentIterable.from(entries.toTypedArray()).uniqueIndex { it.id }

    fun fromId(id: Int): ApiError {
      return API_ERROR_INDEX[id]
        ?: throw IllegalArgumentException("Error ID=${id} is not recognized")
    }
  }

  override fun toString(): String {
    return "httpStatus=${httpStatus.value()} id=$id description=$description"
  }
}

class ApiException(val apiError: ApiError, message: String?, cause: Throwable?, enableSuppression: Boolean, writableStackTrace: Boolean, val structuredArguments: Map<String, Any>) : RuntimeException(message, cause, enableSuppression, writableStackTrace) {
  constructor(apiError: ApiError, message: String?) : this(apiError, message, null as Throwable?)
  constructor(apiError: ApiError, message: String?, cause: Throwable?) : this(
    apiError,
    message,
    cause,
    false,
    true,
    emptyMap()
  )

  constructor(apiError: ApiError, cause: Throwable?) : this(apiError, null, cause, false, true, emptyMap())
  constructor(apiError: ApiError, message: String?, structuredArguments: Map<String, Any>) : this(
    apiError,
    message,
    null,
    structuredArguments
  )

  constructor(apiError: ApiError, message: String?, cause: Throwable?, structuredArguments: Map<String, Any>) : this(
    apiError,
    message,
    cause,
    false,
    true,
    structuredArguments
  )

  constructor(apiError: ApiError, cause: Throwable?, structuredArguments: Map<String, Any>) : this(
    apiError,
    null,
    cause,
    false,
    true,
    structuredArguments
  )
}

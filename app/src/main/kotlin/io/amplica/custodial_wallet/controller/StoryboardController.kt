package io.amplica.custodial_wallet.controller


import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.controller.util.LocalizationUtil
import io.amplica.custodial_wallet.controller.util.ThymeleafHelper
import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.db.repository.UserDetailType
import io.amplica.custodial_wallet.dto.*
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.web.CookieHelper
import io.amplica.custodial_wallet.web.Environment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.math.BigInteger
import java.util.*


@Profile("testui")
@Controller
@RequestMapping("storyboard")
class StoryboardController @Autowired constructor(
  @Qualifier(BeanNames.COOKIE_HELPER) private val cookieHelper: CookieHelper,
  @Qualifier(BeanNames.MESSAGES_LOCALIZATION_UTIL) private val messagesLocalizationUtil: LocalizationUtil,
  private val objectMapper: ObjectMapper,
  @Value("\${unfinished.custodial-wallet.environment}") private val environment: Environment,
  @Qualifier(BeanNames.MATOMO_PROPERTIES) private val matomoProps: MatomoProps,
  @Value("\${sentry.environment}") private val sentryEnv: String,
  @Value("\${sentry.release}") private val sentryRelease: String,
  ) {

  private fun addGlobalModelAttributes(model: Model) {
    model.addAttribute("helper", ThymeleafHelper)
    model.addAttribute("env", environment)
    model.addAttribute("matomo", matomoProps)
    model.addAttribute("sentryEnv", sentryEnv)
    model.addAttribute("sentryRelease", sentryRelease)

    val messageMapJson = objectMapper.writeValueAsString(messagesLocalizationUtil.getEscapedMessagesForLocale(Locale.US))
    model.addAttribute("errorMessages", messageMapJson)
    model.addAttribute("messagesJson", messageMapJson)
  }

  @GetMapping("")
  suspend fun postOnboard(): String {
    return "components/examples/storyboard"
  }

  @GetMapping("siwa/start")
  suspend fun siwaStart(model: Model): String {
    model.addAttribute(
      "props",
      StartProps(
        "siteKey",
        false,
        "MeWe",
        true,
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/start"
  }

  @GetMapping("siwa/ics/start")
  suspend fun siwaIcsStart(model: Model): String {
    model.addAttribute(
      "props",
      StartProps(
        "siteKey",
        false,
        "Chēo",
        false,
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/start"
  }

  @GetMapping("siwa/start/error/email")
  suspend fun siwaStartErrorEmail(model: Model): String {
    model.addAttribute(
      "props",
      StartProps(
        "siteKey",
        false,
        "MeWe",
        true,
        error = GlobalApiError(listOf(ApiError.INVALID_EMAIL))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/start"
  }

  @GetMapping("siwa/ics/start/error/email")
  suspend fun siwaIcsStartErrorEmail(model: Model): String {
    model.addAttribute(
      "props",
      StartProps(
        "siteKey",
        false,
        "Chēo",
        false,
        error = GlobalApiError(listOf(ApiError.INVALID_EMAIL))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/start"
  }

  @GetMapping("siwa/start/error/sms")
  suspend fun siwaStartErrorSms(model: Model): String {
    model.addAttribute(
      "props",
      StartProps(
        "siteKey",
        false,
        "MeWe",
        true,
        error = GlobalApiError(listOf(ApiError.NOT_A_PHONE_NUMBER))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/start"
  }

  @GetMapping("siwa/start/error/blocked_phone")
  suspend fun siwaStartErrorBlockedPhoneNumber(model: Model): String {
    model.addAttribute(
      "props",
      StartProps(
        "siteKey",
        false,
        "MeWe",
        true,
        error = GlobalApiError(listOf(ApiError.BLOCKED_PHONE_NUMBER))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/start"
  }

  @GetMapping("siwa/start/error/session")
  suspend fun siwaStartErrorOther(model: Model): String {
    model.addAttribute(
      "props",
      StartProps(
        "siteKey",
        false,
        "MeWe",
        true,
        error = GlobalApiError(listOf(ApiError.SIWA_SESSION_NOT_FOUND))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/start"
  }

  @GetMapping("siwa/smsSent")
  suspend fun siwaSmsSent(model: Model, response: ServerHttpResponse): String {

    response.addCookie(cookieHelper.createResponseCookie("fake-session-id"))
    model.addAttribute("props",
      OtpVerificationSentProps(
        UserIdentifier("+15555555555", UserIdentifierType.PHONE_NUMBER),
        "MeWe",
        20,
        15000L,
        3,
        "https://mewe.com"
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/smsSent"
  }

  @GetMapping("siwa/smsSent/error/code")
  suspend fun siwaSmsSentErrorCode(model: Model, response: ServerHttpResponse): String {

    response.addCookie(cookieHelper.createResponseCookie("fake-session-id"))
    model.addAttribute("props",
      OtpVerificationSentProps(
        UserIdentifier("+15555555555", UserIdentifierType.PHONE_NUMBER),
        "MeWe",
        20,
        15000L,
        3,
        "https://mewe.com",
        GlobalApiError(listOf(ApiError.SIWA_SESSION_NOT_FOUND_FOR_TOKEN))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/smsSent"
  }

  @GetMapping("siwa/smsSent/error/resend")
  suspend fun siwaSmsSentErrorResend(model: Model, response: ServerHttpResponse): String {

    response.addCookie(cookieHelper.createResponseCookie("fake-session-id"))
    model.addAttribute("props",
      OtpVerificationSentProps(
        UserIdentifier("+15555555555", UserIdentifierType.PHONE_NUMBER),
        "MeWe",
        20,
        15000L,
        3,
        "https://mewe.com",
        GlobalApiError(listOf(ApiError.RESEND_REQUEST_INVALID))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/smsSent"
  }

  @GetMapping("siwa/emailSent")
  suspend fun siwaEmailSent(model: Model): String {
    model.addAttribute("props", MagicLinkVerificationSentProps("example.user@unfinished.com", "10", "MeWe", 15000L, 3, "https://mewe.com"))
    addGlobalModelAttributes(model)
    return "siwa/emailSent"
  }

  @GetMapping("siwa/emailSent/error/resend")
  suspend fun siwaEmailSentErrorResend(model: Model): String {
    model.addAttribute("props",
      MagicLinkVerificationSentProps(
        "example.user@unfinished.com",
        "10",
        "MeWe",
        15000L,
        3,
        "https://mewe.com",
        GlobalApiError(listOf(ApiError.RESEND_REQUEST_INVALID))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/emailSent"
  }

  @GetMapping("siwa/emailLogin")
  suspend fun siwaEmailLogin(model: Model): String {
    model.addAttribute("props", EmailLoginProps("https://mewe.com/register",  "MeWe"))
    addGlobalModelAttributes(model)
    return "siwa/emailLogin"
  }

  @GetMapping("siwa/payloads")
  suspend fun siwaPermissions(model: Model, @RequestParam("claimHandle") claimHandle: Boolean = true): String {
    val permissionMessages = listOf(
      "permission.tombstone",
      "permission.broadcast",
      "permission.reply",
      "permission.update",
      "permission.reaction",
      "permission.account.profile-resources",
      "permission.account.update-identity",
      "permission.account.graph"
    )

    model.addAttribute("props", PayloadsProps(claimHandle, "example.user@unfinished.com", "MeWe", permissionMessages,  "testHandle", null))
    addGlobalModelAttributes(model)
    return "siwa/payloads"
  }

  @GetMapping("siwa/payloads/error/handle")
  suspend fun siwaPermissionsErrorHandle(model: Model, @RequestParam("claimHandle") claimHandle: Boolean = true): String {
    val permissionMessages = listOf(
      "permission.account.update-identity",
      "permission.account.graph"
    )

    model.addAttribute("props",
      PayloadsProps(
        claimHandle,
        "example.user@unfinished.com",
        "MeWe",
        permissionMessages,
        null,
        GlobalApiError(listOf(ApiError.HANDLE_UNAVAILABLE))
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/payloads"
  }

  @GetMapping("siwa/submissionInProgress")
  suspend fun siwaSubmissionInProgress(model: Model): String {
    model.addAttribute("props",
      SiwaSubmissionInProgressProps(
        "https://example.com/submission",
        "https://example.com/target",
        "Chēo",
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/submissionInProgress"
  }

  @GetMapping("siwa/error/internal")
  suspend fun siwaErrorInternal(model: Model): String {
    val apiException = ApiException(ApiError.UNKNOWN_ERROR, "An internal error has occurred")
    model.addAttribute("props",
      ErrorProps(
        apiException.apiError,
        apiException.message,
        apiException.stackTraceToString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "MeWe",
        null,
        null
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/error"
  }

  @GetMapping("siwa/error/integration")
  suspend fun siwaErrorIntegration(model: Model): String {

    val apiException = ApiException(ApiError.INVALID_SIGNATURE, "Signature does not match payload for Siwa request")
    model.addAttribute("props",
      ErrorProps(
        apiException.apiError,
        apiException.message,
        apiException.stackTraceToString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "MeWe",
        null,
        null
      )
    )
    addGlobalModelAttributes(model)
    return "siwa/error"
  }

  @GetMapping("passkey/create")
  suspend fun passkeyWalletCreateWallet(model: Model): String {
    addGlobalModelAttributes(model)
    model.addAttribute("props", CreateWalletProps("https://mewe.com/register", null))
    return "siwa/createWallet"
  }

  @GetMapping("passkey/wallet")
  suspend fun passkeyWalletPhrase(model: Model): String {
    addGlobalModelAttributes(model)
    model.addAttribute("props", PasskeyWalletProps("https://mewe.com/register","ws://localhost:9944", "Passkey Wallet Test", false))
    return "siwa/passkeyWallet"
  }

  @GetMapping("passkey/recovery")
  suspend fun passkeyWalletRecovery(model: Model): String {
    addGlobalModelAttributes(model)
    model.addAttribute("props", PasskeyWalletProps("https://mewe.com/register","ws://localhost:9944", "Passkey Wallet Test", true))
    return "siwa/passkeyWallet"
  }

  @GetMapping("account")
  suspend fun accountPage(model: Model): String {
    addGlobalModelAttributes(model)
    val accountInfo = AccountInfo(
      listOf(UserDetail("test@example.com", UserDetailType.EMAIL)),
      listOf(
        ProviderUserInfo(
          BigInteger.ONE,
          "8hreos",
          BigInteger.ONE,
          "test",
          listOf(UserDetail("test@example.com", UserDetailType.EMAIL)),
          "Kitchen Sink",
          "test",
          BigInteger.ONE,
          listOf(
            "permission.tombstone",
            "permission.broadcast",
            "permission.reply",
            "permission.update",
            "permission.reaction",
            "permission.account.profile-resources",
            "permission.account.update-identity",
            "permission.account.graph"
          ),
          BigInteger.ONE,
          false
        ),
        ProviderUserInfo(
          BigInteger.ONE,
          "8hreos",
          BigInteger.TWO,
          "test",
          listOf(UserDetail("test@example.com", UserDetailType.EMAIL)),
          "Minimal",
          "test",
          BigInteger.ONE,
          emptyList(),
          BigInteger.ONE,
          false
        )
      ),
      false
    )

    model.addAttribute("accountInfo", accountInfo)
    model.addAttribute("accountInfoJson", objectMapper.writeValueAsString(accountInfo))
    model.addAttribute("confirmRevoke", "")
    model.addAttribute("contactAdded", false)
    model.addAttribute("isAccount", true)
    model.addAttribute("isPrivacy", false)
    model.addAttribute("isTerms", false)
    model.addAttribute("passwordEnabled", true)
    model.addAttribute("revocationEnabled", true)
    model.addAttribute("sentryEnv", sentryEnv)
    model.addAttribute("sentryRelease", sentryRelease)
    model.addAttribute("showAddContact", true)
    model.addAttribute("verificationCallbackUrl", "")
    model.addAttribute("passkeyWalletEnabled", true)
    model.addAttribute("changeHandleEnabled", true)
    return "website/account"
  }

}

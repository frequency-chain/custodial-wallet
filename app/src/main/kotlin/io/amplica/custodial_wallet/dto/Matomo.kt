package io.amplica.custodial_wallet.dto

import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.web.Environment
import kotlin.collections.ArrayList

data class MatomoData(
  val title: String?,
  val dimensions: MatomoDimensions?,
  val event: MatomoEvent?
)

data class MatomoDimension(
  val index: Int,
  val value: String
)

enum class MatomoPageName(val pageName: String) {
  SIWA_ERROR("siwa/error"),
  SIWA_START("siwa/start"),
  SIWA_EMAIL_SENT("siwa/emailSent"),
  SIWA_OTP_ENTRY("siwa/smsSent"),
  SIWA_EMAIL_LOGIN("siwa/emailLogin"),
  SIWA_PAYLOADS("siwa/payloads"),
  SIWA_CREATE_WALLET("siwa/createWallet"),
  ;

  fun noPrefix(): String {
    return this.pageName.substringAfter("/")
  }
}

sealed interface MatomoDimensions

data class SiwaMatomoDimensions(
  val dimensions: MutableList<MatomoDimension>
): MatomoDimensions {

  enum class Index(val value: Int) {
    PROVIDER(1),
    INTENT(2),
    CONTACT(3),
    ENVIRONMENT(4),
  }

  enum class IntentType {
    SIGNUP,
    LOGIN
  }

  companion object {
    fun create(provider: String? = null, intent: IntentType? = null, contact: UserIdentifierType? = null, env: Environment? = null): SiwaMatomoDimensions {
      val dimensions = ArrayList<MatomoDimension>()
      provider?.let { dimensions.add(MatomoDimension(Index.PROVIDER.value, provider)) }
      intent?.let { dimensions.add(MatomoDimension(Index.INTENT.value, intent.name)) }
      contact?.let { dimensions.add(MatomoDimension(Index.CONTACT.value, contact.name)) }
      env?.let { dimensions.add(MatomoDimension(Index.ENVIRONMENT.value, env.type)) }
      return SiwaMatomoDimensions(dimensions)
    }
  }

  fun addEnv(env: Environment) {
    dimensions.add(MatomoDimension(Index.ENVIRONMENT.value, env.type))
  }
}

data class MatomoEvent(
  val category: Category,
  val page: String
) {
  enum class Category {
    SIWA,
    PASSKEY_WALLET,
    WEBSITE,
  }
}

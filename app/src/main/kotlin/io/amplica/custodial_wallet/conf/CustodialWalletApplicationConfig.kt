package io.amplica.custodial_wallet.conf

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import com.google.common.collect.FluentIterable
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.strategyobject.substrateclient.common.convert.HexConverter
import com.strategyobject.substrateclient.crypto.KeyPair
import com.strategyobject.substrateclient.crypto.Seed
import com.strategyobject.substrateclient.crypto.sr25519.Sr25519NativeCryptoProvider
import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import com.strategyobject.substrateclient.transport.ws.ExponentialBackoffReconnectionPolicy
import com.strategyobject.substrateclient.transport.ws.ReconnectionPolicy
import com.webauthn4j.WebAuthnManager
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.OrganizationDatabaseService
import io.amplica.custodial_wallet.blocking.BlockingStrategy
import io.amplica.custodial_wallet.blocking.ProviderRateLimitBlockingStrategy
import io.amplica.custodial_wallet.client.captcha.CaptchaClient
import io.amplica.custodial_wallet.client.claim.ClaimServiceClient
import io.amplica.custodial_wallet.client.frequency.gateway.account.AccountServiceClient
import io.amplica.custodial_wallet.client.frequency.gateway.account.conf.AccountServiceClientConf
import io.amplica.custodial_wallet.client.kms.KmsClient
import io.amplica.custodial_wallet.client.notification.NotificationServiceClient
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.SesTemplate
import io.amplica.custodial_wallet.client.redis.dto.SiwaEmailHandling
import io.amplica.custodial_wallet.client.redis.frequency.FrequencyClientRedisClient
import io.amplica.custodial_wallet.controller.DirectLoginRequestCleaner
import io.amplica.custodial_wallet.controller.util.*
import io.amplica.custodial_wallet.db.conf.DbBeanNames
import io.amplica.custodial_wallet.db.repository.KeyDerivationAlgorithmType
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.dto.MatomoProps
import io.amplica.custodial_wallet.email.DefaultEmailService
import io.amplica.custodial_wallet.email.EmailService
import io.amplica.custodial_wallet.email.client.CachingSesClient
import io.amplica.custodial_wallet.email.client.SesClient
import io.amplica.custodial_wallet.email.client.conf.AwsSesProperties
import io.amplica.custodial_wallet.frequency.client.RedisFrequencyClientNonceDatastore
import io.amplica.custodial_wallet.health.*
import io.amplica.custodial_wallet.internationalization.MessageFactory
import io.amplica.custodial_wallet.internationalization.MustacheMessageFactory
import io.amplica.custodial_wallet.internationalization.MustacheTemplateResolver
import io.amplica.custodial_wallet.orchestration.*
import io.amplica.custodial_wallet.orchestration.community_rewards.CommunityRewardsOrchestrationService
import io.amplica.custodial_wallet.orchestration.community_rewards.DefaultCommunityRewardsOrchestrationService
import io.amplica.custodial_wallet.orchestration.deliverability.DefaultDeliverabilityOrchestrationService
import io.amplica.custodial_wallet.orchestration.deliverability.DeliverabilityOrchestrationService
import io.amplica.custodial_wallet.orchestration.organization.DefaultOrganizationOrchestrationService
import io.amplica.custodial_wallet.orchestration.organization.OrganizationOrchestrationService
import io.amplica.custodial_wallet.orchestration.passkey.DefaultPasskeyWalletService
import io.amplica.custodial_wallet.orchestration.passkey.PasskeyWalletService
import io.amplica.custodial_wallet.orchestration.signing.DefaultSigningOrchestrationService
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.*
import io.amplica.custodial_wallet.service.frequency.DefaultFrequencyService
import io.amplica.custodial_wallet.service.frequency.DefaultFrequencyServiceProperties
import io.amplica.custodial_wallet.service.frequency.FrequencyService
import io.amplica.custodial_wallet.service.ics_whitelist.IcsWhitelistService
import io.amplica.custodial_wallet.service.ics_whitelist.PropertyBasedIcsWhitelistService
import io.amplica.custodial_wallet.service.key.DefaultKeyService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.service.organization.DefaultOrganizationService
import io.amplica.custodial_wallet.service.organization.OrganizationService
import io.amplica.custodial_wallet.service.password.DefaultPasswordService
import io.amplica.custodial_wallet.service.password.PasswordService
import io.amplica.custodial_wallet.service.password.util.BCryptPasswordEncoder
import io.amplica.custodial_wallet.service.password.util.PasswordEncoder
import io.amplica.custodial_wallet.service.provider_metadata.CachingProviderMetadataService
import io.amplica.custodial_wallet.service.provider_metadata.DatabaseSourcedProviderMetadataService
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadataService
import io.amplica.custodial_wallet.service.verifiable_credential.DefaultVerifiableCredentialService
import io.amplica.custodial_wallet.service.verifiable_credential.VerifiableCredentialService
import io.amplica.custodial_wallet.service.whitelist_checker.ConfigWhitelistChecker
import io.amplica.custodial_wallet.service.whitelist_checker.WhitelistChecker
import io.amplica.custodial_wallet.template.MustacheTemplateRenderer
import io.amplica.custodial_wallet.template.TemplateRenderer
import io.amplica.custodial_wallet.util.ss58AddressFormatFromByte
import io.amplica.custodial_wallet.validator.PhoneNumberValidator
import io.amplica.custodial_wallet.verifiablecredentials.VerifiableCredentialAuthenticator
import io.amplica.custodial_wallet.verifiablecredentials.canonicalization.TemplateBasedRdfCanonicalizer
import io.amplica.custodial_wallet.verifiablecredentials.codec.Base58MultibaseCodec
import io.amplica.custodial_wallet.verifiablecredentials.codec.KeyType
import io.amplica.custodial_wallet.verifiablecredentials.codec.MultikeyCodec
import io.amplica.custodial_wallet.verifiablecredentials.crypto.Ed25519SignatureManager
import io.amplica.custodial_wallet.verifiablecredentials.cryptosuite.EddsaRdfc2022
import io.amplica.frequency.client.*
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.hashing.HashingStrategy
import io.amplica.frequency.crypto.hashing.PayloadEip712HashingStrategy
import io.amplica.frequency.crypto.hashing.StringEip191HashingStrategy
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toUniversalAddress
import io.amplica.frequency.serialization.*
import io.amplica.frequency.service.DefaultSigningService
import io.amplica.frequency.service.SigningService
import io.amplica.frequency.util.GraphHelper
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.runBlocking
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.web.server.i18n.AcceptHeaderLocaleContextResolver
import org.springframework.web.server.i18n.LocaleContextResolver
import reactor.core.scheduler.Schedulers
import software.amazon.awssdk.services.ses.model.SesException
import java.math.BigInteger
import java.time.Duration
import java.util.*

object BeanNames {
  const val AWS_SES_PROPERTIES = "awsSesProperties"
  const val SMS_PROPERTIES = "smsProperties"
  const val CUSTODIAL_WALLET_ORCHESTRATION_SERVICE_PROPERTIES = "custodialWalletOrchestrationServiceProperties"
  const val LOOKUP_ORCHESTRATION_SERVICE_PROPERTIES = "lookupOrchestrationServiceProperties"
  const val SIGNED_PAYLOAD_ORCHESTRATION_SERVICE_PROPERTIES = "signedPayloadOrchestrationServiceProperties"
  const val FREQUENCY_CLIENT = "frequencyClient"
  const val PHONE_NUMBER_VALIDATOR = "phoneNumberValidator"
  const val GRAPH_PARAM_PROVIDER = "graphParamProvider"
  const val GRAPH_HELPER = "graphHelper"
  const val SMS_MESSAGE_FACTORY = "smsMessageFactory"
  const val AUDIT_UTIL = "auditUtil"
  const val CUSTODIAL_WALLET_ORCHESTRATION_SERVICE = "custodialWalletOrchestrationService"
  const val LOOKUP_ORCHESTRATION_SERVICE = "lookupOrchestrationService"
  const val SIGNED_PAYLOAD_ORCHESTRATION_SERVICE = "signedPayloadOrchestrationService"
  const val SIWA_ORCHESTRATION_SERVICE = "siwaOrchestrationService"
  const val PASSKEY_WALLET_SERVICE = "passkeyWalletService"
  const val REDIS_CLIENT = "redisClient"
  const val RETRY_HELPER = "retryHelper"
  const val NORMALIZATION_UTIL = "normalizationUtil"
  const val DIRECT_LOGIN_REQUEST_CLEANER = "directLoginRequestCleaner"
  const val PHONE_NUMBER_UTIL = "phoneNumberUtil"
  const val KMS_CLIENT = "v2KmsClient"
  const val SIGNING_SERVICE = "signingService"
  const val COOKIE_HELPER = "cookieHelper"
  const val NOTIFICATION_SERVICE_CLIENT = "restNotificationServiceClient"
  const val CLAIM_SERVICE_CLIENT = "restClaimServiceClient"
  const val EMAIL_SERVICE = "emailService"
  const val EMAIL_SERVICE_SIWA = "emailServiceSIWA"
  const val PASSWORD_SERVICE = "passwordService"
  const val PASSWORD_ENCODER = "passwordEncoder"
  const val KEY_SERVICE = "keyService"
  const val WEB_AUTHN_MANAGER = "webAuthnManager"
  const val MUSTACHE_FACTORY = "mustacheFactory"
  const val CAIP_122_MESSAGE_FACTORY = "caip122MessageFactory"
  const val VERIFIABLE_CREDENTIAL_SERVICE = "verifiableCredentialService"
  const val VERIFIABLE_CREDENTIAL_AUTHENTICATOR = "verifiableCredentialAuthenticator"
  const val PROVIDER_METADATA_SERVICE = "providerMetadataService"
  const val CAPTCHA_CLIENT = "hCaptchaClient"
  const val PROVIDER_RATE_LIMIT_BLOCKING_STRATEGY = "providerRateLimitBlockingStrategy"
  const val ORGANIZATION_SERVICE = "organizationService"
  const val ORGANIZATION_ORCHESTRATION_SERVICE = "organizationOrchestrationService"
  const val DID_JSON_TEMPLATE_RENDERER = "didJsonTemplateRenderer"
  const val MATOMO_PROPERTIES = "matomoProperties"
  const val MESSAGES_LOCALIZATION_UTIL = "messagesLocalizationUtil"
  const val MESSAGES_RESOURCE_BUNDLE_BACKED_LOCALTIZATION_UTIL = "messagesResourceBundleBackedLocalizationUtil"
  const val DELIVERABILITY_ORCHESTRATION_SERVICE = "deliverabilityOrchestrationService"
  const val WHITELIST_CHECKER = "whitelistChecker"
  const val COMMUNITY_REWARDS_ORCHESTRATION_SERVICE = "communityRewardsOrchestrationService"
  const val EIP712_OBJECT_MAPPER = "eip712ObjectMapper"
  const val SCALE_OBJECT_MAPPER = "scaleObjectMapper"
  const val SIGNING_ORCHESTRATION_SERVICE = "signingOrchestrationService"
  const val ICS_USER_ORCHESTRATION_SERVICE = "icsUserOrchestrationService"
  const val DEFAULT_ICS_USER_ORCHESTRATION_PROPERTIES = "defaultIcsUserOrchestrationProperties"
  const val ICS_KEY_SERVICE = "icsKeyService"
  const val SS_58_ADDRESS_FORMAT = "ss58AddressFormat"
  const val ICS_WHITELIST_SERVICE = "icsWhitelistService"
  const val FREQUENCY_SERVICE = "frequencyService"
}

object PropertyNames {
  const val SIWA_EMAIL_HANDLING_DEFAULT = "unfinished.custodial-wallet.siwa.use.email.default"
  const val LOCALIZED_MESSAGES_CACHE_SIZE = "unfinished.custodial-wallet.localized.messages.cache.size"
  const val SIGNUP_EXPIRATION_NUM_OF_BLOCKS = "unfinished.custodial-wallet.signup.expiration"
}

@ConfigurationProperties(prefix = "unfinished.custodial-wallet.notification.api.rate.limit")
data class BlockingStrategyConfigurationProperties @ConstructorBinding constructor(
  val count: Int,
  val period: Duration
)

@Configuration
class CustodialWalletApplicationConfig {
  @Bean
  fun ss58AddressFormat(@Value("\${unfinished.custodial-wallet.ss58.type}") ss58TypeByte: Byte): SS58AddressFormat {
    return ss58AddressFormatFromByte(ss58TypeByte)
  }

  @Bean
  fun custodialWalletOrchestrationService(
    @Qualifier(BeanNames.REDIS_CLIENT) redisClient: CustodialWalletRedisClient,
    @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) databaseService: CustodialWalletDatabaseService,
    @Qualifier(BeanNames.SS_58_ADDRESS_FORMAT) ss58AddressFormat: SS58AddressFormat,
    @Qualifier(BeanNames.FREQUENCY_CLIENT) frequencyClient: FrequencyClient,
    @Qualifier(BeanNames.SIGNING_SERVICE) signingService: SigningService,
    @Qualifier(BeanNames.SIGNING_ORCHESTRATION_SERVICE) signingOrchestrationService: SigningOrchestrationService,
    @Qualifier(BeanNames.SMS_MESSAGE_FACTORY) messageFactory: MessageFactory,
    @Qualifier(BeanNames.RETRY_HELPER) retryHelper: RetryHelper,
    @Qualifier(BeanNames.CUSTODIAL_WALLET_ORCHESTRATION_SERVICE_PROPERTIES) properties: DefaultCustodialWalletProperties,
    @Qualifier(DbBeanNames.READ_WRITE_TRANSACTIONAL_OPERATOR) readWriteTransactionalOperator: TransactionalOperator,
    @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) lookupOrchestrationService: LookupOrchestrationService,
    @Qualifier(BeanNames.NOTIFICATION_SERVICE_CLIENT) notificationServiceClient: NotificationServiceClient,
    @Qualifier(BeanNames.EMAIL_SERVICE) emailService: EmailService,
    @Qualifier(BeanNames.PASSWORD_SERVICE) passwordService: PasswordService,
    @Qualifier(BeanNames.PASSKEY_WALLET_SERVICE) passkeyWalletService: PasskeyWalletService,
    @Qualifier(BeanNames.CAPTCHA_CLIENT) captchaClient: CaptchaClient,
  ): CustodialWalletOrchestrationService {

    return DefaultCustodialWalletOrchestrationService(
      properties,
      redisClient,
      frequencyClient,
      signingService,
      signingOrchestrationService,
      databaseService,
      ss58AddressFormat,
      messageFactory,
      retryHelper,
      readWriteTransactionalOperator,
      lookupOrchestrationService,
      notificationServiceClient,
      emailService,
      passwordService,
      passkeyWalletService,
      captchaClient,
    )
  }

  @Bean
  fun passkeyWalletService(
    @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) databaseService: CustodialWalletDatabaseService,
    @Qualifier(BeanNames.SIGNING_ORCHESTRATION_SERVICE) signingOrchestrationService: SigningOrchestrationService,
    @Qualifier(BeanNames.WEB_AUTHN_MANAGER) webAuthnManager: WebAuthnManager,
    @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) lookupOrchestrationService: LookupOrchestrationService,
    @Value("\${unfinished.custodial-wallet.passkey.origin}") origin: String,
    @Value("\${unfinished.custodial-wallet.passkey.rpId}") rpId: String,
    @Value("\${unfinished.custodial-wallet.siwa.passkey-wallet.enabled}") passkeyEnabledForSiwa: Boolean,
    @Value("\${unfinished.custodial-wallet.account.passkey-wallet.enabled}") passkeyEnabledForAccount: Boolean,
    environment: Environment
  ): PasskeyWalletService {
    return DefaultPasskeyWalletService(
      webAuthnManager,
      origin,
      rpId,
      databaseService,
      signingOrchestrationService,
      lookupOrchestrationService,
      environment,
      passkeyEnabledForSiwa || passkeyEnabledForAccount
    )
  }

  @Bean
  fun custodialWalletOrchestrationServiceProperties(
    @Value("\${${PropertyNames.SIGNUP_EXPIRATION_NUM_OF_BLOCKS}}") signupBlockExpiration: Long,
    @Value("\${unfinished.custodial-wallet.redis.expiration}") otpTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.timer.expiration}") timerExpiration: Duration,
    @Value("\${unfinished.custodial-wallet.signup.resend_limit}") resendLimit: Int,
    @Value("\${unfinished.custodial-wallet.hostname}") hostName: String,
    @Value("#{\${unfinished.custodial-wallet.schema.id.permissions.messages}}") schemaIdsPermissionsMap: Map<Set<Int>, String>,
    @Value("\${unfinished.custodial-wallet.output.sms-code.enabled}") outputSmsCodeEnabled: Boolean,
    @Value("\${unfinished.custodial-wallet.record.user-activity.expiration}") userActivityExpiration: Duration,
    @Value("\${unfinished.custodial-wallet.api.account.change-handle.period}") changeHandlePeriod: Duration,
    @Qualifier(BeanNames.AWS_SES_PROPERTIES) awsSesProperties: AwsSesProperties,
    @Qualifier(BeanNames.SMS_PROPERTIES) smsProperties: SmsProperties,
  ) = DefaultCustodialWalletProperties(
    signupBlockExpiration,
    timerExpiration,
    otpTimeout,
    resendLimit,
    hostName,
    schemaIdsPermissionsMap,
    outputSmsCodeEnabled,
    userActivityExpiration,
    changeHandlePeriod,
    awsSesProperties,
    smsProperties,
  )

  @Bean
  fun smsProperties(
    @Value("\${unfinished.custodial-wallet.twilio.from_number}") sourceNumber: String,
    @Value("\${unfinished.custodial-wallet.sms.direct_login_template_name}") directLoginTemplateName: String,
    @Value("\${unfinished.custodial-wallet.sms.add_identifier_template_name}") addIdentifierTemplateName: String,
    @Value("\${unfinished.custodial-wallet.sms.webview_signup_template_name}") webviewSignUpTemplateName: String,
    @Value("\${unfinished.custodial-wallet.sms.webview_login_template_name}") webviewLoginTemplateName: String,
  ) = SmsProperties(
    sourceNumber,
    directLoginTemplateName,
    addIdentifierTemplateName,
    webviewSignUpTemplateName,
    webviewLoginTemplateName
  )

  @Bean
  fun eip712ObjectMapper(
    objectMapper: ObjectMapper,
    @Value("\${unfinished.custodial-wallet.environment}") environment: io.amplica.frequency.serialization.Environment
  ): Eip712ObjectMapper {
    return JacksonBasedEip712ObjectMapper(objectMapper, environment)
  }

  @Bean
  fun scaleObjectMapper(): ScaleObjectMapper {
    return SubstrateScaleObjectMapper(listOf(
      "io.amplica.custodial_wallet.orchestration",
      "io.amplica.frequency.signing_service",
      "com.strategyobject.substrateclient.rpc.api",
      "io.amplica.frequency.client.pallet.msa",
    ))
  }

  @Bean
  fun payloadEip712HashingStrategy(@Qualifier(BeanNames.EIP712_OBJECT_MAPPER) eip712ObjectMapper: Eip712ObjectMapper): HashingStrategy<FrequencySerializable<Any>> {
    return PayloadEip712HashingStrategy(eip712ObjectMapper)
  }

  @Bean
  fun stringEip151HashingStrategy(): StringEip191HashingStrategy {
    return StringEip191HashingStrategy
  }

  @Bean
  fun signingService(
    @Qualifier(BeanNames.SCALE_OBJECT_MAPPER) scaleObjectMapper: ScaleObjectMapper,
    @Qualifier(BeanNames.EIP712_OBJECT_MAPPER) eip712ObjectMapper: Eip712ObjectMapper,
  ): SigningService {
    return DefaultSigningService(
      scaleObjectMapper,
      eip712ObjectMapper
    )
  }

  @Bean
  fun signingOrchestrationService(
    @Qualifier(BeanNames.SIGNING_SERVICE) signingService: SigningService,
    @Value("\${unfinished.custodial-wallet.ss58.type}") ss58TypeByte: Byte,
  ): SigningOrchestrationService {
    val ss58AddressFormat: SS58AddressFormat = ss58AddressFormatFromByte(ss58TypeByte)
    return DefaultSigningOrchestrationService(
      signingService,
      ss58AddressFormat,
    )
  }

  @Bean
  fun frequencyClientNonceDatastore(
    @Qualifier("accountKeyPair") keyPair: AccountKeyPair,
    @Qualifier("frequencyClientRedisClient") frequencyClientRedisClient: FrequencyClientRedisClient
  ): FrequencyClientNonceDatastore {
    return RedisFrequencyClientNonceDatastore(
      HexConverter.toHex(keyPair.toUniversalAddress()),
      frequencyClientRedisClient
    )
  }

  @Bean
  fun reconnectionPolicy(
    @Value("\${unfinished.custodial-wallet.frequency.reconnection.policy.max.attempts}") maxAttempts: Long
  ): ReconnectionPolicy {
    return ExponentialBackoffReconnectionPolicy.builder()
      .notMoreThan(maxAttempts)
      .build()
  }

  @Bean
  fun accountKeyPair(@Value("\${unfinished.custodial-wallet.keypair.seed}") secretSeedHex: String): AccountKeyPair {
    return Sr25519CryptoProvider.createKeyPairFromSeed((HexConverter.toBytes(secretSeedHex)))
  }

  @Bean
  fun keyPair(@Value("\${unfinished.custodial-wallet.keypair.seed}") secretSeedHex: String): KeyPair {
    val sr25519NativeCryptoProvider = Sr25519NativeCryptoProvider()
    return sr25519NativeCryptoProvider.createPairFromSeed(Seed.fromBytes(HexConverter.toBytes(secretSeedHex)))
  }

  @Bean
  fun frequencyClient(
    @Value("\${unfinished.custodial-wallet.frequency.address}") frequencyWebsocketAddress: String,
    @Qualifier("accountKeyPair") keyPair: AccountKeyPair,
    @Value("\${unfinished.custodial-wallet.frequency.dynamicValuesReadLockTimeout}") dynamicValuesReadLockTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.frequency.dynamicValuesWriteLockTimeout}") dynamicValuesWriteLockTimeout: Duration,
    @Qualifier("reconnectionPolicy") reconnectionPolicy: ReconnectionPolicy,
    @Qualifier("frequencyClientNonceDatastore") frequencyClientNonceDatastore: FrequencyClientNonceDatastore,
    @Value("\${unfinished.custodial-wallet.frequency.cacheSize}") cacheSize: Long,
    @Value("\${unfinished.custodial-wallet.frequency.providerNameCache.timeToLive}") providerNameTtl: Duration,
  ): FrequencyClient {
    return CachingFrequencyClient(
      RetryingFrequencyClient(
        SubstrateClientJavaFrequencyClient(
          frequencyWebsocketAddress,
          keyPair,
          listOf(
            "io.amplica.custodial_wallet.util.orchestration",
            "io.amplica.frequency.signing_service",
            "io.amplica.frequency.client"
          ),
          dynamicValuesReadLockTimeout,
          dynamicValuesWriteLockTimeout,
          reconnectionPolicy,
          frequencyClientNonceDatastore
        )
      ),
      cacheSize,
      providerNameTtl,
    )
  }

  @Bean
  fun phoneNumberUtil(): PhoneNumberUtil {
    return PhoneNumberUtil.getInstance()
  }

  @Bean
  fun phoneNumberValidator(phoneNumberUtil: PhoneNumberUtil): PhoneNumberValidator {
    return PhoneNumberValidator(phoneNumberUtil)
  }


  @Bean
  fun objectMapper(): ObjectMapper {
    return Jackson2ObjectMapperBuilder().failOnUnknownProperties(false)
      .serializationInclusion(JsonInclude.Include.NON_NULL)
      .build()
  }

  @Bean
  fun messageSource(): MessageSource? {
    val messageSource = ResourceBundleMessageSource()
    messageSource.setBasenames("messages")
    messageSource.setDefaultEncoding("UTF-8")
    return messageSource
  }

  @Bean
  fun chainHealthIndicator(
    @Qualifier(BeanNames.FREQUENCY_CLIENT) frequencyClient: FrequencyClient,
  ): ChainHealthIndicator {
    return ChainHealthIndicator(frequencyClient)
  }

  @Bean
  fun sesClientHealthIndicator(
    @Qualifier("v2SesClient") sesClient: SesClient,
    @Value("\${unfinished.custodial-wallet.aws-ses.signup_template_name}") signupTemplateName: String
  ): SesClientHealthIndicator {
    return SesClientHealthIndicator(sesClient, signupTemplateName)
  }

  @Bean
  fun kmsClientHealthIndicator(
    @Qualifier("v2KmsClient") kmsClient: KmsClient
  ): KmsClientHealthIndicator {
    return KmsClientHealthIndicator(kmsClient)
  }

  @Bean
  fun accountServiceHealthIndicator(
    @Qualifier(AccountServiceClientConf.BeanNames.ACCOUNT_SERVICE_CLIENT) accountServiceClient: AccountServiceClient,
  ): AccountServiceHealthIndicator {
    return AccountServiceHealthIndicator(accountServiceClient)
  }

  @Bean
  fun notificationServiceHealthIndicator(
    @Qualifier(BeanNames.NOTIFICATION_SERVICE_CLIENT) notificationServiceClient: NotificationServiceClient,
  ): NotificationServiceHealthIndicator {
    return NotificationServiceHealthIndicator(notificationServiceClient)
  }

  @Bean
  fun hCaptchaHealthIndicator(
    @Qualifier(BeanNames.CAPTCHA_CLIENT) captchaClient: CaptchaClient,
  ): CaptchaHealthIndicator {
    return CaptchaHealthIndicator(captchaClient)
  }

  @Bean
  fun lookupOrchestrationServiceProperties(
    @Value("\${unfinished.lookup.default.max.records}") defaultMaxRecords: Int,
    @Value("\${unfinished.custodial-wallet.ss58.type}") ss58TypeByte: Byte,
    @Value("#{\${unfinished.custodial-wallet.handle.reserved_words}}") reservedWords: Set<String>,
    @Value("#{\${unfinished.custodial-wallet.handle.blocked_characters}}") blockedCharacters: Set<Char>,
    @Value("\${unfinished.custodial-wallet.hostname}") applicationOrigin: String,
    @Value("\${unfinished.custodial-wallet.whitelist.provider.localhost.allowed}") providerWhitelistAllowLocalhost: Boolean,
  ): DefaultLookupOrchestrationServiceProperties {
    val ss58AddressFormat = ss58AddressFormatFromByte(ss58TypeByte)
    return DefaultLookupOrchestrationServiceProperties(
      defaultMaxRecords,
      ss58AddressFormat,
      reservedWords,
      blockedCharacters,
      applicationOrigin,
      providerWhitelistAllowLocalhost
    )
  }

  @Bean
  fun lookupOrchestrationService(
    @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE_PROPERTIES) properties: DefaultLookupOrchestrationServiceProperties,
    @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) custodialWalletDatabaseService: CustodialWalletDatabaseService,
    @Qualifier(BeanNames.FREQUENCY_CLIENT) frequencyClient: FrequencyClient,
    @Qualifier(BeanNames.REDIS_CLIENT) redisClient: CustodialWalletRedisClient,
    @Qualifier(BeanNames.PROVIDER_METADATA_SERVICE) providerMetadataService: ProviderMetadataService,
    @Qualifier(BeanNames.KMS_CLIENT) kmsClient: KmsClient,
    @Qualifier(BeanNames.GRAPH_HELPER) graphHelper: GraphHelper,
  ): LookupOrchestrationService {
    return DefaultLookupOrchestrationService(
      properties,
      custodialWalletDatabaseService,
      frequencyClient,
      redisClient,
      providerMetadataService,
      kmsClient,
      graphHelper,
    )
  }

  @Bean
  fun localeContextResolver(): LocaleContextResolver {
    val lcr = AcceptHeaderLocaleContextResolver()
    lcr.defaultLocale = Locale.US
    return lcr
  }

  @Bean
  fun mustacheFactory(): MustacheFactory {
    return DefaultMustacheFactory()
  }

  @Bean
  fun smsMessageFactory(@Qualifier(BeanNames.MUSTACHE_FACTORY) mustacheFactory: MustacheFactory,resourceLoader: ResourceLoader): MessageFactory {
    val templateResolver = MustacheTemplateResolver(mustacheFactory, resourceLoader)
    return MustacheMessageFactory(templateResolver)
  }

  @Bean
  fun caip122MessageFactory(@Qualifier(BeanNames.MUSTACHE_FACTORY) mustacheFactory: MustacheFactory, resourceLoader: ResourceLoader): MessageFactory {
    val templateResolver = MustacheTemplateResolver(mustacheFactory, resourceLoader, "classpath:/templates/caip122/")
    return MustacheMessageFactory(templateResolver)
  }

  @Bean
  fun didJsonTemplateRenderer(
    @Qualifier(BeanNames.MUSTACHE_FACTORY) mustacheFactory: MustacheFactory
  ): TemplateRenderer {
    return MustacheTemplateRenderer(mustacheFactory, "templates/.well-known/did.json.mustache")
  }

  @Bean
  fun graphHelper(@Qualifier(BeanNames.GRAPH_PARAM_PROVIDER) graphParamProvider: GraphParamProvider): GraphHelper {
    return GraphHelper(graphParamProvider.getGraphConfiguration())
  }

  @Bean
  fun signedPayloadOrchestrationService(
    @Qualifier(BeanNames.SIGNED_PAYLOAD_ORCHESTRATION_SERVICE_PROPERTIES) properties: DefaultSignedPayloadOrchestrationServiceProperties,
    @Qualifier(BeanNames.SIGNING_ORCHESTRATION_SERVICE) signingOrchestrationService: SigningOrchestrationService,
    @Qualifier(BeanNames.REDIS_CLIENT) redisClient: CustodialWalletRedisClient,
    @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) lookupOrchestrationService: LookupOrchestrationService,
    @Qualifier(BeanNames.NOTIFICATION_SERVICE_CLIENT) notificationServiceClient: NotificationServiceClient,
    @Qualifier(BeanNames.SMS_MESSAGE_FACTORY) messageFactory: MessageFactory,
    @Qualifier(BeanNames.EMAIL_SERVICE) emailService: EmailService,
  ): SignedPayloadOrchestrationService {
    return DefaultSignedPayloadOrchestrationService(
      properties,
      signingOrchestrationService,
      redisClient,
      lookupOrchestrationService,
      notificationServiceClient,
      messageFactory,
      emailService,
    )
  }

  @Bean
  fun signedPayloadOrchestrationServiceProperties(
    @Value("\${unfinished.custodial-wallet.redis.expiration}") otpTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.hostname}") hostName: String,
    @Qualifier(BeanNames.AWS_SES_PROPERTIES) awsSesProperties: AwsSesProperties,
    @Qualifier(BeanNames.SMS_PROPERTIES) smsProperties: SmsProperties,
    @Value("\${${PropertyNames.SIGNUP_EXPIRATION_NUM_OF_BLOCKS}}") payloadBlockExpiration: Long
  ): DefaultSignedPayloadOrchestrationServiceProperties {
    return DefaultSignedPayloadOrchestrationServiceProperties(
      payloadBlockExpiration,
      otpTimeout,
      hostName,
      awsSesProperties,
      smsProperties
    )
  }

  @Bean
  fun passwordService(
    @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) databaseService: CustodialWalletDatabaseService,
    @Qualifier(BeanNames.PASSWORD_ENCODER) passwordEncoder: PasswordEncoder,
    @Value("\${unfinished.custodial-wallet.password.encoder_type}") encoderType: String,
    @Qualifier(DbBeanNames.READ_WRITE_TRANSACTIONAL_OPERATOR) readWriteTransactionalOperator: TransactionalOperator
  ): PasswordService {
    return DefaultPasswordService(
      databaseService,
      passwordEncoder,
      KeyDerivationAlgorithmType.valueOf(encoderType.uppercase(Locale.getDefault())),
      readWriteTransactionalOperator
    )
  }

  @Bean
  fun passwordEncoder(
    @Value("\${unfinished.custodial-wallet.password.encoder_rounds}") encoderNumberOfRounds: Int
  ): PasswordEncoder {
    return BCryptPasswordEncoder(encoderNumberOfRounds)
  }

  @Bean
  fun auditUtil(@Value("\${unfinished.custodial-wallet.audit-session-record.enabled}") enabled: Boolean): AuditUtil {
    return AuditUtil(enabled)
  }

  @Bean
  fun retryHelper(
    @Qualifier(BeanNames.REDIS_CLIENT) redisClient: CustodialWalletRedisClient,
    @Value("\${unfinished.custodial-wallet.incorrect.token.retry.limit}") incorrectTokenRetryLimit: Int
  ): RetryHelper {
    return RetryHelper(redisClient, incorrectTokenRetryLimit)
  }

  @Bean
  fun normalizationUtil(@Qualifier(BeanNames.PHONE_NUMBER_UTIL) phoneNumberUtil: PhoneNumberUtil): NormalizationUtil {
    return NormalizationUtil(phoneNumberUtil)
  }

  @Bean
  fun directLoginRequestCleaner(@Qualifier(BeanNames.NORMALIZATION_UTIL) normalizationUtil: NormalizationUtil): DirectLoginRequestCleaner {
    return DirectLoginRequestCleaner(normalizationUtil)
  }

  @PostConstruct
  fun mdcReactorHandling() {
    Schedulers.onScheduleHook("mdc") { runnable: Runnable ->
      val map = MDC.getCopyOfContextMap()
      Runnable {
        if (map != null) {
          MDC.setContextMap(map)
        }
        try {
          runnable.run()
        } finally {
          MDC.clear()
        }
      }
    }
  }

  @Bean
  fun siwaOrchestrationService(
    @Qualifier(BeanNames.SMS_PROPERTIES) smsProperties: SmsProperties,
    @Value("\${unfinished.custodial-wallet.hostname}") hostName: String,
    @Value("\${unfinished.custodial-wallet.incorrect.token.retry.limit}") incorrectTokenRetryLimit: Int,
    @Value("#{\${unfinished.custodial-wallet.schema.id.permissions.messages}}") schemaIdsPermissionsMap: Map<Set<Int>, String>,
    @Value("\${${PropertyNames.SIGNUP_EXPIRATION_NUM_OF_BLOCKS}}") signupBlockExpiration: Long,
    @Value("\${unfinished.custodial-wallet.signup.resend_limit}") resendLimit: Int,
    @Value("\${unfinished.custodial-wallet.signup.resend_freebee}") resendFreebee: Int,
    @Value("\${unfinished.custodial-wallet.ss58.type}") ss58TypeByte: Byte,
    @Value("\${unfinished.custodial-wallet.timer.expiration}") resendInterval: Duration,
    @Value("\${unfinished.custodial-wallet.redis.expiration}") redisExpiration: Duration,
    @Value("\${unfinished.custodial-wallet.admin.shared.secret.rebuild.signup.payload}") providerAdminSharedSecret: String,
    @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) databaseService: CustodialWalletDatabaseService,
    @Qualifier(BeanNames.EMAIL_SERVICE_SIWA) otpEmailService: EmailService,
    @Qualifier(BeanNames.EMAIL_SERVICE) magicLinkEmailService: EmailService,
    @Qualifier(BeanNames.KEY_SERVICE) keyService: KeyService,
    @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) lookupOrchestrationService: LookupOrchestrationService,
    @Qualifier(BeanNames.SIGNING_ORCHESTRATION_SERVICE) signingOrchestrationService: SigningOrchestrationService,
    @Qualifier(BeanNames.VERIFIABLE_CREDENTIAL_SERVICE) verifiableCredentialService: VerifiableCredentialService,
    @Qualifier(BeanNames.NOTIFICATION_SERVICE_CLIENT) notificationServiceClient: NotificationServiceClient,
    @Qualifier(BeanNames.REDIS_CLIENT) redisClient: CustodialWalletRedisClient,
    @Qualifier(BeanNames.PROVIDER_RATE_LIMIT_BLOCKING_STRATEGY) blockingStrategy: BlockingStrategy,
    @Qualifier(BeanNames.GRAPH_HELPER) graphHelper: GraphHelper,
    @Qualifier(BeanNames.SMS_MESSAGE_FACTORY) smsMessageFactory: MessageFactory,
    @Qualifier(BeanNames.PHONE_NUMBER_VALIDATOR) phoneNumberValidator: PhoneNumberValidator,
    @Value("\${unfinished.custodial-wallet.chain.reference}") chainReference: String,
    @Qualifier(BeanNames.CAIP_122_MESSAGE_FACTORY) caip122messageFactory: MessageFactory,
    @Qualifier(BeanNames.CAPTCHA_CLIENT) captchaClient: CaptchaClient,
    @Value("\${${PropertyNames.SIWA_EMAIL_HANDLING_DEFAULT}}") siwaEmailHandling: SiwaEmailHandling,
    @Qualifier(BeanNames.PASSKEY_WALLET_SERVICE) passkeyWalletService: PasskeyWalletService,
    @Value("\${unfinished.custodial-wallet.siwa.passkey-wallet.enabled}") passkeyActive: Boolean,
    @Qualifier(BeanNames.NORMALIZATION_UTIL) normalizationUtil: NormalizationUtil,
    @Qualifier(BeanNames.WHITELIST_CHECKER) whitelistChecker: WhitelistChecker,
    @Qualifier(DbBeanNames.DELEGATING_TRANSACTIONAL_OPERATOR) delegatingTransactionalOperator: DelegatingTransactionalOperator,
    @Value("\${unfinished.custodial-wallet.frequency.developer-terms.copy}") developerTermsCopy: String,
    @Value("\${unfinished.custodial-wallet.environment}") environment: io.amplica.custodial_wallet.web.Environment,
    @Qualifier(BeanNames.ICS_WHITELIST_SERVICE) icsWhitelistService: IcsWhitelistService,
    @Qualifier(BeanNames.FREQUENCY_SERVICE) frequencyService: FrequencyService,
  ): SiwaOrchestrationService {
    val ss58AddressFormat: SS58AddressFormat = ss58AddressFormatFromByte(ss58TypeByte)
    val properties = DefaultSiwaOrchestrationProperties(
      IdentifierVerificationProperties(resendInterval, resendLimit, resendFreebee, incorrectTokenRetryLimit),
      schemaIdsPermissionsMap,
      signupBlockExpiration,
      smsProperties,
      ss58AddressFormat,
      redisExpiration,
      siwaEmailHandling,
      passkeyActive,
      environment,
    )

    val siwaEmailHandlingToEmailService =
      mapOf(SiwaEmailHandling.OTP to otpEmailService, SiwaEmailHandling.MAGIC_LINK to magicLinkEmailService)

    return TimingSiwaOrchestrationService(
      DefaultSiwaOrchestrationService(
        properties,
        databaseService,
        siwaEmailHandlingToEmailService,
        keyService,
        lookupOrchestrationService,
        signingOrchestrationService,
        verifiableCredentialService,
        notificationServiceClient,
        redisClient,
        graphHelper,
        smsMessageFactory,
        phoneNumberValidator,
        blockingStrategy,
        hostName,
        chainReference,
        caip122messageFactory,
        captchaClient,
        providerAdminSharedSecret,
        passkeyWalletService,
        normalizationUtil,
        whitelistChecker,
        delegatingTransactionalOperator,
        developerTermsCopy,
        icsWhitelistService,
        frequencyService,
      )
    )
  }

  @Bean
  fun keyService(
    @Value("\${unfinished.custodial-wallet.ss58.type}") ss58TypeByte: Byte,
    @Value("\${unfinished.custodial-wallet.aws-kms.key_alias}") kmsKeyAlias: String,
    @Qualifier(BeanNames.KMS_CLIENT) kmsClient: KmsClient,
    @Qualifier(BeanNames.GRAPH_HELPER) graphHelper: GraphHelper,
  ): KeyService {
    val ss58AddressFormat: SS58AddressFormat = ss58AddressFormatFromByte(ss58TypeByte)

    return DefaultKeyService(kmsClient, graphHelper, ss58AddressFormat, kmsKeyAlias)
  }

  @Bean
  fun webAuthnManager(): WebAuthnManager {
    return WebAuthnManager.createNonStrictWebAuthnManager()
  }

  @Bean
  fun verifiableCredentialAuthenticator(): VerifiableCredentialAuthenticator {
    return VerifiableCredentialAuthenticator(
      listOf(
        EddsaRdfc2022(
          TemplateBasedRdfCanonicalizer,
          Base58MultibaseCodec,
          Ed25519SignatureManager,
          Ed25519SignatureManager
        )
      )
    )
  }

  @Bean
  fun verifiableCredentialService(
    @Qualifier(BeanNames.DID_JSON_TEMPLATE_RENDERER) didJsonTemplateRenderer: TemplateRenderer,
    @Value("\${unfinished.custodial-wallet.hostname}") hostNameWithProtocol: String,
    @Value("\${unfinished.custodial-wallet.keypair.ed25519.seed}") ed25519SeedHex: String,
    @Qualifier(BeanNames.VERIFIABLE_CREDENTIAL_AUTHENTICATOR) verifiableCredentialAuthenticator: VerifiableCredentialAuthenticator,
    @Value("\${unfinished.custodial-wallet.verifiable-credentials.valid-from-delay}") validFromDelay: Duration,
  ): VerifiableCredentialService {

    val hostName = hostNameWithProtocol.substringAfter("://")
    val keyPair = Ed25519SignatureManager.newKeyPairFromSeed(HexConverter.toBytes(ed25519SeedHex))

    return DefaultVerifiableCredentialService(
      didJsonTemplateRenderer,
      hostName,
      keyPair,
      verifiableCredentialAuthenticator,
      MultikeyCodec(KeyType.Ed25519, Base58MultibaseCodec),
      MultikeyCodec(KeyType.Sr25519, Base58MultibaseCodec),
      MultikeyCodec(KeyType.ECDSA, Base58MultibaseCodec),
      validFromDelay,
    )
  }

  /**
   * NOTE: This bean may fail to initialize due to `SesClient` imposing draconian rate limits (1 rps)
   * --see `AwsSdkSesAsyncClient` for details.
   */
  @Bean
  fun cachingSesClient(
    @Qualifier("v2SesClient") sesClient: SesClient,
    @Value("\${unfinished.custodial-wallet.ses.cache-timeout}") cacheTimeout: Duration,
    @Qualifier(BeanNames.REDIS_CLIENT) redisClient: CustodialWalletRedisClient,
  ): SesClient {
    val templates = runBlocking {
      //Grabs the templates from AWS and if we are throttled just reads from Redis, this is a best effort without distributed locking
      //I'm assuming the odds are low that the latency is so bad that this will come up before redis is seeded initially PMF 20241001
      //If this ends up not being the case we will need a backoff strategy but that may cause other stuff in the toolchain to timeout
      //and bork deployments in new and interesting ways
      try {
        val coroutineScopedTemplates = FluentIterable.from(sesClient.listAllTemplates()).transform { it.name }.toSet()
        redisClient.saveSesTemplates(FluentIterable.from(coroutineScopedTemplates).transform { SesTemplate(it) }
          .toSet())
        coroutineScopedTemplates
      } catch (x: SesException) {
        if (!x.isThrottlingException) {
          throw x
        } else {
          FluentIterable.from(redisClient.findAllSesTemplates()).transform { it.sesTemplateName }.toSet()
        }
      }
    }
    return CachingSesClient(sesClient, cacheTimeout, templates)
  }

  @Bean
  fun emailService(
    @Qualifier("cachingSesClient") sesClient: SesClient,
    @Qualifier("awsSesProperties") awsSesProperties: AwsSesProperties,
    @Qualifier("secondaryAwsSesProperties") secondaryAwsSesProperties: AwsSesProperties,
    @Value("\${unfinished.custodial-wallet.redis.expiration}") otpTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.hostname}") hostName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.primary.percentage.int}") primaryPercentage: Int,
  ): EmailService = DefaultEmailService(
    sesClient,
    primaryPercentage,
    awsSesProperties,
    secondaryAwsSesProperties,
    otpTimeout,
    hostName,
  )

  @Bean
  fun emailServiceSIWA(
    @Qualifier("cachingSesClient") sesClient: SesClient,
    @Qualifier("awsSesPropertiesSIWA") awsSesProperties: AwsSesProperties,
    @Qualifier("secondaryAwsSesPropertiesSIWA") secondaryAwsSesProperties: AwsSesProperties,
    @Value("\${unfinished.custodial-wallet.redis.expiration}") otpTimeout: Duration,
    @Value("\${unfinished.custodial-wallet.hostname}") hostName: String,
    @Value("\${unfinished.custodial-wallet.aws-ses.primary.percentage.int}") primaryPercentage: Int,
  ): EmailService = DefaultEmailService(
    sesClient,
    primaryPercentage,
    awsSesProperties,
    secondaryAwsSesProperties,
    otpTimeout,
    hostName,
  )

  @Bean
  fun providerRateLimitBlockingStrategy(
    @Qualifier("redisClient") redisClient: CustodialWalletRedisClient,
    blockingStrategyConfigurationProperties: BlockingStrategyConfigurationProperties
  ): ProviderRateLimitBlockingStrategy {
    return ProviderRateLimitBlockingStrategy(
      redisClient,
      blockingStrategyConfigurationProperties.count,
      blockingStrategyConfigurationProperties.period,
    )
  }

  @Bean
  fun organizationService(
    @Qualifier(DbBeanNames.ORGANIZATION_DATABASE_SERVICE) organizationDatabaseService: OrganizationDatabaseService,
    @Qualifier(DbBeanNames.READ_WRITE_TRANSACTIONAL_OPERATOR) readWriteTransactionalOperator: TransactionalOperator,
  ): OrganizationService = DefaultOrganizationService(organizationDatabaseService, readWriteTransactionalOperator)

  @Bean
  fun providerMetadataService(
    @Qualifier(DbBeanNames.ORGANIZATION_DATABASE_SERVICE) organizationDatabaseService: OrganizationDatabaseService,
    @Value("\${unfinished.custodial-wallet.provider_metadata.cache.size}") cacheSize: Int,
    @Value("\${unfinished.custodial-wallet.provider_metadata.cache.expiration}") timeToLive: Duration,
  ): ProviderMetadataService {
    return CachingProviderMetadataService(
      DatabaseSourcedProviderMetadataService(organizationDatabaseService),
      timeToLive,
      cacheSize,
    )
  }

  @Bean
  fun matomoProperties(
    @Value("\${unfinished.custodial-wallet.matomo.enabled}") enabled: Boolean,
    @Value("\${unfinished.custodial-wallet.matomo.url}") url: String,
    @Value("\${unfinished.custodial-wallet.matomo.site-id}") siteId: Int,
    @Value("\${unfinished.custodial-wallet.matomo.heartbeat-enabled}") enabledHeartbeat: Boolean,
  ): MatomoProps {
    return MatomoProps(enabled, url, siteId, enabledHeartbeat, null)
  }

  @Bean
  fun messagesLocalizationUtil(
    @Qualifier(BeanNames.MESSAGES_RESOURCE_BUNDLE_BACKED_LOCALTIZATION_UTIL) localizationUtil: LocalizationUtil,
    @Value("\${${PropertyNames.LOCALIZED_MESSAGES_CACHE_SIZE}}") maxCacheSize: Long
  ): LocalizationUtil {
    return CachingLocalizationUtil(localizationUtil, maxCacheSize)
  }

  @Bean
  fun messagesResourceBundleBackedLocalizationUtil(): LocalizationUtil {
    return ResourceBundleBackedLocalizationUtil("messages")
  }

  @Bean
  fun deliverabilityOrchestrationService(
    @Qualifier(BeanNames.NORMALIZATION_UTIL) normalizationUtil: NormalizationUtil,
    @Qualifier(BeanNames.NOTIFICATION_SERVICE_CLIENT) notificationServiceClient: NotificationServiceClient,
  ): DeliverabilityOrchestrationService {
    return DefaultDeliverabilityOrchestrationService(normalizationUtil, notificationServiceClient)
  }

  @Bean
  fun whitelistChecker(
    @Value("\${unfinished.custodial-wallet.admin.plus-address.whitelist-enabled}") plusAddressingWhitelistActive: Boolean,
    @Value("\${unfinished.custodial-wallet.admin.plus-address.whitelist}") plusAddressUrls: Set<String>,
    @Value("\${unfinished.custodial-wallet.admin.load-capacity-email-testing.enabled}") loadCapacityEmailTestingEnabled: Boolean,
    @Value("\${unfinished.custodial-wallet.admin.load-capacity-email-testing.whitelist}") loadCapacityEmailTestingDomains: Set<String>,
  ): WhitelistChecker {
    return ConfigWhitelistChecker(
      plusAddressingWhitelistActive,
      plusAddressUrls,
      loadCapacityEmailTestingEnabled,
      loadCapacityEmailTestingDomains,
    )
  }

  @Bean
  fun organizationOrchestrationService(
    @Value("\${unfinished.custodial-wallet.hostname}") hostName: String,
    @Qualifier(BeanNames.ORGANIZATION_SERVICE) organizationService: OrganizationService,
    @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) lookupOrchestrationService: LookupOrchestrationService,
  ): OrganizationOrchestrationService {
    return DefaultOrganizationOrchestrationService(
      hostName,
      organizationService,
      lookupOrchestrationService
    )
  }

  @Bean
  fun communityRewardsOrchestrationService(
    @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) databaseService: CustodialWalletDatabaseService,
    @Qualifier(BeanNames.CAIP_122_MESSAGE_FACTORY) caip122messageFactory: MessageFactory,
    @Qualifier(BeanNames.KEY_SERVICE) keyService: KeyService,
    @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) lookupOrchestrationService: LookupOrchestrationService,
    @Qualifier(BeanNames.SIGNING_ORCHESTRATION_SERVICE) signingOrchestrationService: SigningOrchestrationService,
    @Qualifier(BeanNames.CLAIM_SERVICE_CLIENT) claimServiceClient: ClaimServiceClient,
    @Value("\${unfinished.custodial-wallet.ss58.type}") ss58TypeByte: Byte,
    @Qualifier(DbBeanNames.DELEGATING_TRANSACTIONAL_OPERATOR) delegatingTransactionalOperator: DelegatingTransactionalOperator,
  ): CommunityRewardsOrchestrationService {
    val ss58AddressFormat: SS58AddressFormat = ss58AddressFormatFromByte(ss58TypeByte)
    return DefaultCommunityRewardsOrchestrationService(
      databaseService,
      claimServiceClient,
      caip122messageFactory,
      keyService,
      lookupOrchestrationService,
      signingOrchestrationService,
      ss58AddressFormat,
      delegatingTransactionalOperator
    )
  }

  @Bean
  fun icsWhitelistService(
    @Value("\${unfinished.custodial-wallet.siwa.ics.provider-msa-whitelist}") icsProviderMsaIdWhitelist: Set<BigInteger>,
  ): IcsWhitelistService {
    return PropertyBasedIcsWhitelistService(icsProviderMsaIdWhitelist)
  }

  @Bean
  fun frequencyService(
    @Qualifier("accountKeyPair") accountKeyPair: AccountKeyPair,
    @Qualifier(BeanNames.SIGNING_SERVICE) signingService: SigningService,
    @Qualifier(BeanNames.FREQUENCY_CLIENT) frequencyClient: FrequencyClient,
    @Value("\${${PropertyNames.SIGNUP_EXPIRATION_NUM_OF_BLOCKS}}") signupBlockExpiration: Long,
  ): FrequencyService {
    val properties = DefaultFrequencyServiceProperties(
      extrinsicExpirationBlocks = signupBlockExpiration,
    )

    return DefaultFrequencyService(
      accountKeyPair.toPublicKey(),
      signingService,
      frequencyClient,
      properties,
    )
  }

}

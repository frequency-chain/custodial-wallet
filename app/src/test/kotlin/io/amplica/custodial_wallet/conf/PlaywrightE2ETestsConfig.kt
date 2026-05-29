package io.amplica.custodial_wallet.conf

import com.fasterxml.jackson.databind.ObjectMapper
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.db.repository.organization.ReactiveOrganizationAssetRepository
import io.amplica.custodial_wallet.db.repository.organization.ReactiveOrganizationRepository
import io.amplica.custodial_wallet.db.repository.organization.ReactiveProviderFrequencyAccountRepository
import io.amplica.custodial_wallet.db.repository.organization.ReactiveWhitelistedOriginDescriptorRepository
import io.amplica.custodial_wallet.db.repository.organization.application.ReactiveProviderApplicationAssetRepository
import io.amplica.custodial_wallet.db.repository.organization.application.ReactiveProviderApplicationRepository
import io.amplica.custodial_wallet.db.repository.organization.application.ReactiveProviderApplicationWhitelistedOriginDescriptorRepository
import io.amplica.custodial_wallet.util.ApiUtil
import io.amplica.custodial_wallet.util.DbUtil
import io.amplica.custodial_wallet.util.SesUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile


object BeanNames {
  const val SES_UTIL = "sesUtil"
  const val API_UTIL = "apiUtil"
  const val DB_UTIL = "dbUtil"
}

@Configuration
@Profile("test")
class PlaywrightE2ETestsConfig {
  @Autowired
  lateinit var testRestTemplate: TestRestTemplate

  // This value is overridden by `LocalStackTestContainer` to point to the local SES
  @Value("\${unfinished.custodial-wallet.aws-ses.service_endpoint}")
  lateinit var sesEndpoint: String

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Autowired
  lateinit var reactiveAuditSessionRecordRepository: ReactiveAuditSessionRecordRepository

  @Autowired
  lateinit var reactiveCredentialRepository: ReactiveCredentialRepository

  @Autowired
  lateinit var reactiveCredentialTransportRepository: ReactiveCredentialTransportRepository

  @Autowired
  lateinit var reactiveWalletMetadataRepository: ReactiveWalletMetadataRepository

  @Autowired
  lateinit var reactiveProviderExternalUserDetailRepository: ReactiveProviderExternalUserDetailRepository

  @Autowired
  lateinit var reactiveProviderExternalUserRepository: ReactiveProviderExternalUserRepository

  @Autowired
  lateinit var reactiveUserAccountRepository: ReactiveUserAccountRepository

  @Autowired
  lateinit var reactiveUserAccountUserIdentifierRepository: ReactiveUserAccountUserIdentifierRepository

  @Autowired
  lateinit var reactiveUserIdentifierRepository: ReactiveUserIdentifierRepository

  @Autowired
  lateinit var reactiveUserKeyDataRepository: ReactiveUserKeyDataRepository

  @Autowired
  lateinit var reactiveUserPasswordRepository: ReactiveUserPasswordRepository

  @Autowired
  lateinit var reactiveWalletRepository: ReactiveWalletRepository

  @Autowired
  lateinit var reactiveOrganizationAssetRepository: ReactiveOrganizationAssetRepository

  @Autowired
  lateinit var reactiveOrganizationRepository: ReactiveOrganizationRepository

  @Autowired
  lateinit var reactiveProviderFrequencyAccountRepository: ReactiveProviderFrequencyAccountRepository

  @Autowired
  lateinit var reactiveWhitelistedOriginDescriptorRepository: ReactiveWhitelistedOriginDescriptorRepository

  @Autowired
  lateinit var reactiveProviderApplicationAssetRepository: ReactiveProviderApplicationAssetRepository

  @Autowired
  lateinit var reactiveProviderApplicationRepository: ReactiveProviderApplicationRepository

  @Autowired
  lateinit var reactiveProviderApplicationWhitelistedOriginDescriptorRepository: ReactiveProviderApplicationWhitelistedOriginDescriptorRepository

  @Autowired
  lateinit var reactiveOptInRepository: ReactiveOptInRepository

  @Bean
  fun sesUtil() = SesUtil(testRestTemplate, sesEndpoint)

  @Bean
  fun apiUtil() = ApiUtil(testRestTemplate, objectMapper)

  @Bean
  fun dbUtil() = DbUtil(
    reactiveAuditSessionRecordRepository,
    reactiveCredentialRepository,
    reactiveCredentialTransportRepository,
    reactiveWalletMetadataRepository,
    reactiveProviderExternalUserDetailRepository,
    reactiveProviderExternalUserRepository,
    reactiveUserAccountRepository,
    reactiveUserAccountUserIdentifierRepository,
    reactiveUserIdentifierRepository,
    reactiveUserKeyDataRepository,
    reactiveUserPasswordRepository,
    reactiveWalletRepository,
    reactiveOrganizationAssetRepository,
    reactiveOrganizationRepository,
    reactiveProviderFrequencyAccountRepository,
    reactiveWhitelistedOriginDescriptorRepository,
    reactiveProviderApplicationAssetRepository,
    reactiveProviderApplicationWhitelistedOriginDescriptorRepository,
    reactiveProviderApplicationRepository,
    reactiveOptInRepository
  )
}
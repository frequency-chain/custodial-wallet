package io.amplica.custodial_wallet.util

import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.db.repository.organization.*
import io.amplica.custodial_wallet.db.repository.organization.application.*
import io.amplica.custodial_wallet.service.organization.OrganizationData
import io.amplica.custodial_wallet.service.organization.OriginDescriptor
import io.amplica.custodial_wallet.service.organization.ProviderApplicationData
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.net.URI

class DbUtil(
  private val reactiveAuditSessionRecordRepository: ReactiveAuditSessionRecordRepository,
  private val reactiveCredentialRepository: ReactiveCredentialRepository,
  private val reactiveCredentialTransportRepository: ReactiveCredentialTransportRepository,
  private val reactiveWalletMetadataRepository: ReactiveWalletMetadataRepository,
  private val reactiveProviderExternalUserDetailRepository: ReactiveProviderExternalUserDetailRepository,
  private val reactiveProviderExternalUserRepository: ReactiveProviderExternalUserRepository,
  private val reactiveUserAccountRepository: ReactiveUserAccountRepository,
  private val reactiveUserAccountUserIdentifierRepository: ReactiveUserAccountUserIdentifierRepository,
  private val reactiveUserIdentifierRepository: ReactiveUserIdentifierRepository,
  private val reactiveUserKeyDataRepository: ReactiveUserKeyDataRepository,
  private val reactiveUserPasswordRepository: ReactiveUserPasswordRepository,
  private val reactiveWalletRepository: ReactiveWalletRepository,
  private val reactiveOrganizationAssetRepository: ReactiveOrganizationAssetRepository,
  private val reactiveOrganizationRepository: ReactiveOrganizationRepository,
  private val reactiveProviderFrequencyAccountRepository: ReactiveProviderFrequencyAccountRepository,
  private val reactiveWhitelistedOriginDescriptorRepository: ReactiveWhitelistedOriginDescriptorRepository,
  private val reactiveProviderApplicationAssetRepository: ReactiveProviderApplicationAssetRepository,
  private val reactiveProviderApplicationWhitelistedOriginDescriptorRepository: ReactiveProviderApplicationWhitelistedOriginDescriptorRepository,
  private val reactiveProviderApplicationRepository: ReactiveProviderApplicationRepository,
  private val reactiveOptInRepository: ReactiveOptInRepository,
  ) {

  companion object {
    val LOG: Logger = LoggerFactory.getLogger(DbUtil::class.java)

    // Alice Provider/Application metadata
    val ALICE_ORGANIZATION = OrganizationData(
      setOf(1.toBigInteger()),
      "MeWe",
      "mewe",
      listOf(OriginDescriptor("https", "mewe.com")),
      emptyMap()
    )
    val ALICE_PROVIDER_APPLICATION = ProviderApplicationData(
      URI("application.mewe.com"),
      "MeWeApp",
      "meweapp",
      listOf(OriginDescriptor("app", "mewe.com")),
      emptyMap()
    )

    // Alice Provider/Application metadata
    val BOB_ORGANIZATION = OrganizationData(
      setOf(2.toBigInteger()),
      "Bob",
      "bob",
      listOf(OriginDescriptor("https", "bob.com")),
      emptyMap()
    )
    val BOB_PROVIDER_APPLICATION = ProviderApplicationData(
      URI("application.bob.com"),
      "Bob",
      "bob",
      listOf(OriginDescriptor("app", "bob.com")),
      emptyMap()
    )
  }

  fun saveOrganizationData(organizationList: List<Pair<OrganizationData, ProviderApplicationData>> = listOf(
    ALICE_ORGANIZATION to ALICE_PROVIDER_APPLICATION,
    BOB_ORGANIZATION to BOB_PROVIDER_APPLICATION,
  )): Unit = runBlocking {
    organizationList.forEach { (organizationData, applicationData) ->
      val organization = saveOrganization(Organization.create(organizationData.displayName, organizationData.shortcode))
      val providerFrequencyAccounts = organizationData.msaIds.map { msaId ->
        saveProviderFrequencyAccount(ProviderFrequencyAccount.create(msaId, organization.id!!))
      }
      organizationData.whitelistedOrigins.forEach { origin ->
        saveWhitelistedOriginDescriptor(
          WhitelistedOriginDescriptor.create(organization.id!!, origin.scheme, origin.domain)
        )
      }
      saveProviderApplicationData(providerFrequencyAccounts[0].id!!, applicationData)
    }
  }

  private fun saveProviderApplicationData(
    providerFrequencyAccountId: BigInteger,
    applicationData: ProviderApplicationData,
  ): Unit = runBlocking{
    val providerApplication = saveProviderApplication(
      ProviderApplication.create(
        providerFrequencyAccountId,
        applicationData.verifiedCredentialUrl.toASCIIString(),
        applicationData.displayName,
        applicationData.shortcode
      )
    )
    applicationData.whitelistedOrigins.forEach { origin ->
      saveProviderApplicationWhitelistedOriginDescriptor(
        ProviderApplicationWhitelistedOriginDescriptor.create(providerApplication.id!!, origin.scheme, origin.domain)
      )
    }
  }

  fun saveOrganization(organization: Organization): Organization = runBlocking {
    val response = reactiveOrganizationRepository.save(organization).awaitSingleOrNull()
    response ?: throw RuntimeException("Failed to save to database: $organization")
  }

  fun saveProviderFrequencyAccount(account: ProviderFrequencyAccount): ProviderFrequencyAccount = runBlocking {
    val response = reactiveProviderFrequencyAccountRepository.save(account).awaitSingleOrNull()
    response ?: throw RuntimeException("Failed to save to database: $account")
  }

  fun saveWhitelistedOriginDescriptor(origin: WhitelistedOriginDescriptor): WhitelistedOriginDescriptor = runBlocking {
    val response = reactiveWhitelistedOriginDescriptorRepository.save(origin).awaitSingleOrNull()
    response ?: throw RuntimeException("Failed to save to database: $origin")
  }

  fun saveProviderApplication(providerApplication: ProviderApplication): ProviderApplication = runBlocking {
    val response = reactiveProviderApplicationRepository.save(providerApplication).awaitSingleOrNull()
    response ?: throw RuntimeException("Failed to save to database: $providerApplication")
  }

  fun saveProviderApplicationWhitelistedOriginDescriptor(origin: ProviderApplicationWhitelistedOriginDescriptor): ProviderApplicationWhitelistedOriginDescriptor =
    runBlocking {
      val response = reactiveProviderApplicationWhitelistedOriginDescriptorRepository.save(origin).awaitSingleOrNull()
      response ?: throw RuntimeException("Failed to save to database: $origin")
    }

  fun deleteFromAllTables(): Unit = runBlocking {
    reactiveAuditSessionRecordRepository.deleteAll().awaitSingleOrNull()
    reactiveOptInRepository.deleteAll().awaitSingleOrNull()
    reactiveWalletMetadataRepository.deleteAll().awaitSingleOrNull()
    reactiveCredentialTransportRepository.deleteAll().awaitSingleOrNull()
    reactiveCredentialRepository.deleteAll().awaitSingleOrNull()
    reactiveWalletRepository.deleteAll().awaitSingleOrNull()
    reactiveProviderExternalUserDetailRepository.deleteAll().awaitSingleOrNull()
    reactiveProviderExternalUserRepository.deleteAll().awaitSingleOrNull()
    reactiveUserAccountUserIdentifierRepository.deleteAll().awaitSingleOrNull()
    reactiveUserIdentifierRepository.deleteAll().awaitSingleOrNull()
    reactiveUserKeyDataRepository.deleteAll().awaitSingleOrNull()
    reactiveUserPasswordRepository.deleteAll().awaitSingleOrNull()
    reactiveUserAccountRepository.deleteAll().awaitSingleOrNull()
    reactiveProviderApplicationWhitelistedOriginDescriptorRepository.deleteAll().awaitSingleOrNull()
    reactiveProviderApplicationAssetRepository.deleteAll().awaitSingleOrNull()
    reactiveProviderApplicationRepository.deleteAll().awaitSingleOrNull()
    reactiveProviderFrequencyAccountRepository.deleteAll().awaitSingleOrNull()
    reactiveWhitelistedOriginDescriptorRepository.deleteAll().awaitSingleOrNull()
    reactiveOrganizationAssetRepository.deleteAll().awaitSingleOrNull()
    reactiveOrganizationRepository.deleteAll().awaitSingleOrNull()
  }
}
package io.amplica.custodial_wallet

import io.amplica.custodial_wallet.db.repository.organization.*
import io.amplica.custodial_wallet.db.repository.organization.application.*
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import reactor.core.publisher.Mono
import java.math.BigInteger
import java.net.URI
import java.time.Instant

class ReactiveOrganizationDatabaseService(
  private val reactiveOrganizationRepository: ReactiveOrganizationRepository,
  private val reactiveProviderFrequencyAccountRepository: ReactiveProviderFrequencyAccountRepository,
  private val reactiveWhitelistedOriginDescriptorRepository: ReactiveWhitelistedOriginDescriptorRepository,
  private val reactiveOrganizationAssetRepository: ReactiveOrganizationAssetRepository,
  private val reactiveProviderApplicationRepository: ReactiveProviderApplicationRepository,
  private val reactiveProviderApplicationWhitelistedOriginDescriptorRepository: ReactiveProviderApplicationWhitelistedOriginDescriptorRepository,
  private val reactiveProviderApplicationAssetRepository: ReactiveProviderApplicationAssetRepository,
  private val delegatingTransactionalOperator: DelegatingTransactionalOperator,
) : OrganizationDatabaseService {

  override suspend fun saveOrganization(organization: Organization): BigInteger {
    return delegatingTransactionalOperator.executeReadWrite {
      if (organization.id != null) {
        throw IllegalArgumentException("'Organization.id' must be null")
      }

      reactiveOrganizationRepository.save(organization).awaitSingle().id!!
    }
  }

  override suspend fun updateOrganization(organization: Organization) {
    delegatingTransactionalOperator.executeReadWrite {
      if (organization.id == null) {
        throw IllegalArgumentException("'Organization.id' must not be null")
      }

      // NOTE: We need to manually set the last modified date
      val updatedOrganization = organization.copy(lastModified = Instant.now().toEpochMilli())

      reactiveOrganizationRepository.save(updatedOrganization).awaitSingle()
    }
  }

  private fun hydrateTransientOrganizationProperties(organization: Organization): Mono<Organization> {
    return reactiveProviderFrequencyAccountRepository.findAllByOrganizationId(organization.id!!).collectList()
      .flatMap { msaIds ->
        reactiveOrganizationAssetRepository.findAllByOrganizationId(organization.id).collectList()
          .flatMap { assets ->
            reactiveWhitelistedOriginDescriptorRepository.findAllByOrganizationId(organization.id).collectList()
              .map { origins ->
                organization.apply {
                  this.providerFrequencyAccounts = msaIds
                  this.assets = assets
                  this.whitelistedOriginDescriptors = origins
                }
              }
          }
      }
  }

  override suspend fun findOneOrganizationById(id: BigInteger): Organization? {
    return delegatingTransactionalOperator.executeReadOnly {
      reactiveOrganizationRepository.findById(id).flatMap { organization ->
        when (organization) {
          null -> Mono.empty()
          else -> hydrateTransientOrganizationProperties(organization)
        }
      }.awaitSingleOrNull()
    }
  }

  override suspend fun findOneOrganizationByProviderMsaId(providerMsaId: BigInteger): Organization? {
    return delegatingTransactionalOperator.executeReadOnly {
      reactiveOrganizationRepository.findOneOrganizationByMsaId(providerMsaId).flatMap { organization ->
        when (organization) {
          null -> Mono.empty()
          else -> hydrateTransientOrganizationProperties(organization)
        }
      }.awaitSingleOrNull()
    }
  }

  override suspend fun deleteAllProviderFrequencyAccountsByOrganizationId(organizationId: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderFrequencyAccountRepository.deleteAllByOrganizationId(organizationId).awaitSingleOrNull()
    }
  }

  override suspend fun saveProviderFrequencyAccount(account: ProviderFrequencyAccount): BigInteger {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderFrequencyAccountRepository.save(account).awaitSingle().id!!
    }
  }

  override suspend fun deleteAllAssetsByOrganizationId(organizationId: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveOrganizationAssetRepository.deleteAllByOrganizationId(organizationId).awaitSingleOrNull()
    }
  }

  override suspend fun saveOrganizationAsset(asset: OrganizationAsset): BigInteger {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveOrganizationAssetRepository.save(asset).awaitSingle().id!!
    }
  }

  override suspend fun deleteAllWhitelistedOriginDescriptorsByOrganizationId(organizationId: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveWhitelistedOriginDescriptorRepository.deleteAllByOrganizationId(organizationId).awaitSingleOrNull()
    }
  }

  override suspend fun saveWhitelistedOriginDescriptor(descriptor: WhitelistedOriginDescriptor): BigInteger {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveWhitelistedOriginDescriptorRepository.save(descriptor).awaitSingle().id!!
    }
  }

  override suspend fun findOneProviderFrequencyAccountByMsaId(providerMsaId: BigInteger): ProviderFrequencyAccount? {
    return delegatingTransactionalOperator.executeReadOnly {
      reactiveProviderFrequencyAccountRepository.findByMsaId(providerMsaId).flatMap { providerFrequencyAccount ->
        when (providerFrequencyAccount) {
          null -> Mono.empty()
          else -> hydrateTransientProviderFrequencyAccountProperties(providerFrequencyAccount)
        }
      }.awaitSingleOrNull()
    }
  }

  private fun hydrateTransientProviderFrequencyAccountProperties(providerFrequencyAccount: ProviderFrequencyAccount): Mono<ProviderFrequencyAccount> {
    return reactiveProviderApplicationRepository.findAllByProviderFrequencyAccountId(providerFrequencyAccount.id!!).flatMap {
      providerApplication -> hydrateTransientProviderApplicationProperties(providerApplication)
    }.collectList().map {
      providerApplications -> providerFrequencyAccount.apply {
        this.providerApplications = providerApplications
      }
    }
  }

  override suspend fun updateProviderFrequencyAccount(providerFrequencyAccessAccount: ProviderFrequencyAccount) {
    delegatingTransactionalOperator.executeReadWrite {
      if (providerFrequencyAccessAccount.id == null) {
        throw IllegalArgumentException("'providerFrequencyAccessAccount.id' must not be null")
      }

      val updatedProviderFrequencyAccessAccount = providerFrequencyAccessAccount.copy(lastModified = Instant.now().toEpochMilli())
      reactiveProviderFrequencyAccountRepository.save(updatedProviderFrequencyAccessAccount).awaitSingle()
    }
  }

  private fun hydrateTransientProviderApplicationProperties(providerApplication: ProviderApplication): Mono<ProviderApplication> {
    return reactiveProviderApplicationAssetRepository.findAllByProviderApplicationId(providerApplication.id!!).collectList()
          .flatMap { providerAssets ->
            reactiveProviderApplicationWhitelistedOriginDescriptorRepository.findAllByProviderApplicationId(providerApplication.id).collectList()
              .map { applicationOrigin ->
                providerApplication.apply {
                  this.providerApplicationAssets = providerAssets
                  this.providerApplicationWhitelistedOriginDescriptors = applicationOrigin
                }

          }
      }
  }

  override suspend fun findProviderApplicationByUrl(
    providerMsaId: BigInteger,
    verifiedCredentialUrl: URI
  ): ProviderApplication? {
    return delegatingTransactionalOperator.executeReadOnly {
      reactiveProviderApplicationRepository.findOneOrganizationByMsaIdAndVerifiedCredentialUrl(providerMsaId, verifiedCredentialUrl.toASCIIString()).flatMap { providerApplication ->
        when (providerApplication) {
          null -> Mono.empty()
          else -> hydrateTransientProviderApplicationProperties(providerApplication)
        }
      }.awaitSingleOrNull()
    }
  }

  override suspend fun findProviderApplicationById(
    providerApplicationId: BigInteger
  ): ProviderApplication? {
    return delegatingTransactionalOperator.executeReadOnly {
      reactiveProviderApplicationRepository.findById(providerApplicationId).flatMap { providerApplication ->
        when (providerApplication) {
          null -> Mono.empty()
          else -> hydrateTransientProviderApplicationProperties(providerApplication)
        }
      }.awaitSingleOrNull()
    }
  }

  override suspend fun saveProviderApplication(providerApplication: ProviderApplication): BigInteger {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderApplicationRepository.save(providerApplication).awaitSingle().id!!
    }
  }

  override suspend fun deleteAllAssetsByProviderApplicationId(providerApplicationId: BigInteger) {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderApplicationAssetRepository.deleteAllByProviderApplicationId(providerApplicationId).awaitSingleOrNull()
    }
  }

  override suspend fun saveProviderApplicationAsset(asset: ProviderApplicationAsset): BigInteger {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderApplicationAssetRepository.save(asset).awaitSingle().id!!
    }
  }

  override suspend fun deleteAllWhitelistedOriginDescriptorsByProviderApplicationId(providerApplicationId: BigInteger) {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderApplicationWhitelistedOriginDescriptorRepository.deleteAllByProviderApplicationId(providerApplicationId).awaitSingleOrNull()
    }
  }

  override suspend fun saveProviderApplicationWhitelistedOriginDescriptor(descriptor: ProviderApplicationWhitelistedOriginDescriptor): BigInteger {
    return delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderApplicationWhitelistedOriginDescriptorRepository.save(descriptor).awaitSingle().id!!
    }
  }

  override suspend fun deleteProviderApplicationById(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      deleteAllAssetsByProviderApplicationId(id)
      deleteAllWhitelistedOriginDescriptorsByProviderApplicationId(id)
      reactiveProviderApplicationRepository.deleteById(id).awaitSingleOrNull()
    }
  }

  override suspend fun deleteProviderFrequencyAccountByMsaId(msaId: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderFrequencyAccountRepository.deleteByMsaId(msaId).awaitSingleOrNull()
    }
  }

  override suspend fun deleteOrganizationAssetById(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveOrganizationAssetRepository.deleteById(id).awaitSingleOrNull()
    }
  }

  override suspend fun deleteWhitelistedOriginDescriptorById(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveWhitelistedOriginDescriptorRepository.deleteById(id).awaitSingleOrNull()
    }
  }

  override suspend fun deleteProviderApplicationAssetById(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderApplicationAssetRepository.deleteById(id).awaitSingleOrNull()
    }
  }

  override suspend fun deleteProviderApplicationWhitelistedOriginDescriptorById(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderApplicationWhitelistedOriginDescriptorRepository.deleteById(id).awaitSingleOrNull()
    }
  }

  override suspend fun deleteAllApplicationsByProviderFrequencyAccountId(id: BigInteger) {
    delegatingTransactionalOperator.executeReadWrite {
      reactiveProviderApplicationRepository.deleteAllByProviderFrequencyAccountId(id).awaitSingleOrNull()
    }
  }
}
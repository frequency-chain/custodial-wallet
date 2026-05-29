package io.amplica.custodial_wallet.service.organization

import io.amplica.custodial_wallet.OrganizationDatabaseService
import io.amplica.custodial_wallet.db.repository.organization.*
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplication
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationAsset
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationWhitelistedOriginDescriptor
import io.amplica.custodial_wallet.service.util.createTransactionalOperatorDouble
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.net.URI

class DefaultOrganizationServiceTest {

  companion object {
    object TestData {
      val MSA_ID = 247.toBigInteger()
      val MSA_ID_NEW = 252.toBigInteger()
      const val DISPLAY_NAME = "Example Provider"
      const val SHORT_CODE = "exmpl"
      const val BRAND_LOGO_URL = "https://example.com/brand_logo.png"
      const val WHITELISTED_SCHEME = "https"
      const val WHITELISTED_DOMAIN = "example.com"
      object Organization {
        val DATA = OrganizationData(
          setOf(MSA_ID),
          DISPLAY_NAME,
          SHORT_CODE,
          listOf(OriginDescriptor(WHITELISTED_SCHEME, WHITELISTED_DOMAIN)),
          mapOf(AssetType.BRAND_LOGO to Asset(BRAND_LOGO_URL))
        )

        val DATA_NEW = OrganizationData(
          setOf(MSA_ID_NEW),
          DISPLAY_NAME,
          SHORT_CODE,
          listOf(),
          mapOf()
        )
      }

      val ORGANIZATION_ID = 127.toBigInteger()
      val PROVIDER_FREQUENCY_ACCOUNT_ID = 99.toBigInteger()
      val WHITELISTED_ORIGIN_DESCRIPTOR_ID = 33.toBigInteger()
      val ORGANIZATION_ASSET_ID = 11.toBigInteger()
      val PROVIDER_APPLICATION_ID = 25.toBigInteger()
      val PROVIDER_APPLICATION_ASSET_ID = 66.toBigInteger()
      val PROVIDER_WHITELISTED_ORIGIN_DESCRIPTOR_ID = 34.toBigInteger()
      val VERIFIED_CREDENTIAL_URL = "https://example.com"

      object ProviderApplication {
        val DATA = ProviderApplicationData(
          URI(VERIFIED_CREDENTIAL_URL),
          DISPLAY_NAME,
          SHORT_CODE,
          listOf(OriginDescriptor(WHITELISTED_SCHEME, WHITELISTED_DOMAIN)),
          mapOf(AssetType.BRAND_LOGO to Asset(BRAND_LOGO_URL))
        )
      }

      object ProviderFrequencyAccount {
        val DATA = ProviderFrequencyAccountData(
          MSA_ID,
          ORGANIZATION_ID,
          listOf(TestData.ProviderApplication.DATA)
        )
        val DATA_NEW = ProviderFrequencyAccountData(
          MSA_ID,
          ORGANIZATION_ID,
          listOf()
        )
      }
    }
  }

  private val organizationDatabaseService: OrganizationDatabaseService = mock()
  private val transactionalOperator = createTransactionalOperatorDouble()
  private val service = DefaultOrganizationService(organizationDatabaseService, transactionalOperator)

  private val mockProviderApplicationEntity = ProviderApplication.create(
    TestData.PROVIDER_FREQUENCY_ACCOUNT_ID,
    TestData.VERIFIED_CREDENTIAL_URL,
    TestData.DISPLAY_NAME,
    TestData.SHORT_CODE,
  ).copy(
    // Mock the values created by R2DBC / Postgres
    id = TestData.PROVIDER_APPLICATION_ID,
    version = 0L
  ).apply {
    this.providerApplicationAssets = listOf(
      ProviderApplicationAsset.create(
        TestData.PROVIDER_APPLICATION_ID,
        OrganizationAssetType.BRAND_LOGO,
        TestData.BRAND_LOGO_URL
      ).copy (
        id = TestData.PROVIDER_APPLICATION_ASSET_ID
      )
    )
    this.providerApplicationWhitelistedOriginDescriptors = listOf(
      ProviderApplicationWhitelistedOriginDescriptor.create(
        TestData.PROVIDER_APPLICATION_ID,
        TestData.WHITELISTED_SCHEME,
        TestData.WHITELISTED_DOMAIN
      ).copy (
        id = TestData.PROVIDER_WHITELISTED_ORIGIN_DESCRIPTOR_ID
      )
    )
  }

  private val mockOrganizationEntity = Organization.create(TestData.DISPLAY_NAME, TestData.SHORT_CODE).copy(
    // Mock the values created by R2DBC / Postgres
    id = TestData.ORGANIZATION_ID,
    version = 0L
  ).apply {
    // Mock the hydrated object graph
    this.providerFrequencyAccounts = listOf(
      ProviderFrequencyAccount.create(TestData.MSA_ID, TestData.ORGANIZATION_ID).copy(
        // Mock the values created by R2DBC / Postgres
        id = TestData.PROVIDER_FREQUENCY_ACCOUNT_ID,
        version = 0L
      ).apply {
        this.providerApplications = listOf(mockProviderApplicationEntity)
      }
    )
    this.assets = listOf(
      OrganizationAsset.create(
        TestData.ORGANIZATION_ID,
        OrganizationAssetType.BRAND_LOGO,
        TestData.BRAND_LOGO_URL
      ).copy (
        id = TestData.ORGANIZATION_ASSET_ID
      )
    )
    this.whitelistedOriginDescriptors = listOf(
      WhitelistedOriginDescriptor.create(
        TestData.ORGANIZATION_ID,
        TestData.WHITELISTED_SCHEME,
        TestData.WHITELISTED_DOMAIN
      ).copy (
        id = TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID
      )
    )
  }

  @Test
  fun getOrganization(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findOneOrganizationById(
        eq(TestData.ORGANIZATION_ID)
      )
    ).thenReturn(mockOrganizationEntity)

    // WHEN
    val organizationData = service.getOrganization(TestData.ORGANIZATION_ID)

    // THEN
    Assertions.assertThat(organizationData).isEqualTo(TestData.Organization.DATA)
  }

  private fun verifyOrganizationDatabaseServiceAncillarySaveMethodsInvoked(isUpdate: Boolean): Unit = runBlocking {
    // Verify that each of the `save*` database service methods for data related to an organization were invoked correctly
    verify(organizationDatabaseService).saveProviderFrequencyAccount(argThat {
      this.msaId == TestData.MSA_ID &&
          this.organizationId == TestData.ORGANIZATION_ID &&
          if(isUpdate) {this.id == TestData.PROVIDER_FREQUENCY_ACCOUNT_ID} else {this.id == null}
    })
    verify(organizationDatabaseService).saveOrganizationAsset(argThat {
      this.organizationId == TestData.ORGANIZATION_ID &&
          this.assetType == OrganizationAssetType.BRAND_LOGO &&
          this.url == TestData.BRAND_LOGO_URL &&
          if(isUpdate) {this.id == TestData.ORGANIZATION_ASSET_ID} else {this.id == null}
    })
    verify(organizationDatabaseService).saveWhitelistedOriginDescriptor(argThat {
      this.organizationId == TestData.ORGANIZATION_ID &&
          this.scheme == TestData.WHITELISTED_SCHEME &&
          this.domain == TestData.WHITELISTED_DOMAIN &&
          if(isUpdate) {this.id == TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID} else {this.id == null}
    })
  }

  private fun verifyProviderApplicationDatabaseServiceAncillarySaveMethodsInvoked(isUpdate: Boolean): Unit = runBlocking {
    // Verify that each of the `save*` database service methods for data related to an organization were invoked correctly
    verify(organizationDatabaseService).saveProviderApplicationAsset(argThat {
      this.providerApplicationId == TestData.PROVIDER_APPLICATION_ID &&
              this.assetType == OrganizationAssetType.BRAND_LOGO &&
              this.url == TestData.BRAND_LOGO_URL &&
              if(isUpdate) {this.id == TestData.ORGANIZATION_ASSET_ID} else {this.id == null}
    })
    verify(organizationDatabaseService).saveProviderApplicationWhitelistedOriginDescriptor(argThat {
      this.providerApplicationId == TestData.PROVIDER_APPLICATION_ID &&
              this.scheme == TestData.WHITELISTED_SCHEME &&
              this.domain == TestData.WHITELISTED_DOMAIN &&
              if(isUpdate) {this.id == TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID} else {this.id == null}
    })
  }

  @Test
  fun saveOrganization(): Unit = runBlocking {
    // GIVEN
    val data = TestData.Organization.DATA

    // Mock database service `save*` methods
    whenever(organizationDatabaseService.saveOrganization(any())).thenReturn(TestData.ORGANIZATION_ID)
    whenever(organizationDatabaseService.saveProviderFrequencyAccount(any())).thenReturn(TestData.PROVIDER_FREQUENCY_ACCOUNT_ID)
    whenever(organizationDatabaseService.saveOrganizationAsset(any())).thenReturn(TestData.ORGANIZATION_ASSET_ID)
    whenever(organizationDatabaseService.saveWhitelistedOriginDescriptor(any())).thenReturn(TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID)

    // WHEN
    service.saveOrganization(data)

    // THEN
    verify(organizationDatabaseService).saveOrganization(argThat {
      this.displayName == TestData.DISPLAY_NAME && this.shortCode == TestData.SHORT_CODE
    })

    verifyOrganizationDatabaseServiceAncillarySaveMethodsInvoked(false)
  }

  @Test
  fun updateOrganization(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findOneOrganizationById(
        eq(TestData.ORGANIZATION_ID)
      )
    ).thenReturn(mockOrganizationEntity)

    // Mock database service methods
    whenever(organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)).thenReturn(mockOrganizationEntity.providerFrequencyAccounts.get(0))
    whenever(organizationDatabaseService.saveOrganization(any())).thenReturn(TestData.ORGANIZATION_ID)
    whenever(organizationDatabaseService.saveProviderFrequencyAccount(any())).thenReturn(TestData.PROVIDER_FREQUENCY_ACCOUNT_ID)
    whenever(organizationDatabaseService.saveOrganizationAsset(any())).thenReturn(TestData.ORGANIZATION_ASSET_ID)
    whenever(organizationDatabaseService.saveWhitelistedOriginDescriptor(any())).thenReturn(TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID)

    // WHEN
    service.updateOrganization(TestData.ORGANIZATION_ID, TestData.Organization.DATA)

    // Verify organization entity and related entities are updated/saved
    verify(organizationDatabaseService).updateOrganization(argThat {
      this.id == TestData.ORGANIZATION_ID &&
              this.displayName == TestData.DISPLAY_NAME &&
              this.shortCode == TestData.SHORT_CODE
    })
    verifyOrganizationDatabaseServiceAncillarySaveMethodsInvoked(true)
  }

  @Test
  fun updateOrganizationWithDeletions(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findOneOrganizationById(
        eq(TestData.ORGANIZATION_ID)
      )
    ).thenReturn(mockOrganizationEntity)

    // Mock database service methods
    whenever(organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)).thenReturn(null)
    whenever(organizationDatabaseService.saveOrganization(any())).thenReturn(TestData.ORGANIZATION_ID)
    whenever(organizationDatabaseService.saveProviderFrequencyAccount(any())).thenReturn(TestData.PROVIDER_FREQUENCY_ACCOUNT_ID)
    whenever(organizationDatabaseService.saveOrganizationAsset(any())).thenReturn(TestData.ORGANIZATION_ASSET_ID)
    whenever(organizationDatabaseService.saveWhitelistedOriginDescriptor(any())).thenReturn(TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID)

    // WHEN
    service.updateOrganization(TestData.ORGANIZATION_ID, TestData.Organization.DATA_NEW)

    // Verify related data is deleted
    verify(organizationDatabaseService).deleteProviderFrequencyAccountByMsaId(eq(TestData.MSA_ID))
    verify(organizationDatabaseService).deleteOrganizationAssetById(eq(TestData.ORGANIZATION_ASSET_ID))
    verify(organizationDatabaseService).deleteWhitelistedOriginDescriptorById(eq(TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID))

    // Verify organization entity and related entities are updated/saved
    verify(organizationDatabaseService).updateOrganization(argThat {
      this.id == TestData.ORGANIZATION_ID &&
          this.displayName == TestData.DISPLAY_NAME &&
          this.shortCode == TestData.SHORT_CODE
    })
    verify(organizationDatabaseService).saveProviderFrequencyAccount(argThat {
      this.msaId == TestData.MSA_ID_NEW &&
          this.organizationId == TestData.ORGANIZATION_ID
    })
  }

  @Test
  fun unlinkOrganization(): Unit = runBlocking {
    // WHEN
    service.deleteOrganizationProviderFrequencyAccounts(TestData.ORGANIZATION_ID)

    // THEN
    verify(organizationDatabaseService).deleteAllProviderFrequencyAccountsByOrganizationId(eq(TestData.ORGANIZATION_ID))
  }

  @Test
  fun getOrganizationByMsaId(): Unit = runBlocking {
    // GIVEN
    val msaId = TestData.MSA_ID

    whenever(
      organizationDatabaseService.findOneOrganizationByProviderMsaId(eq(TestData.MSA_ID))
    ).thenReturn(mockOrganizationEntity)

    // WHEN
    val result = service.getOrganizationByMsaId(msaId)

    // THEN
    verify(organizationDatabaseService).findOneOrganizationByProviderMsaId(eq(msaId))
    Assertions.assertThat(result).usingRecursiveComparison().isEqualTo(
      Pair(TestData.ORGANIZATION_ID, TestData.Organization.DATA)
    )
  }

  @Test
  fun getProviderApplicationByUrl(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findProviderApplicationByUrl(
        eq(TestData.MSA_ID),
        eq(URI(TestData.VERIFIED_CREDENTIAL_URL))
      )
    ).thenReturn(mockProviderApplicationEntity)

    // WHEN
    val providerApplicationData = service.getProviderApplicationByUrl(
      TestData.MSA_ID,
      URI(TestData.VERIFIED_CREDENTIAL_URL)
    )

    // THEN
    Assertions.assertThat(providerApplicationData).isEqualTo(TestData.ProviderApplication.DATA)
  }

  @Test
  fun getProviderFrequencyAccount(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)
    ).thenReturn(mockOrganizationEntity.providerFrequencyAccounts[0])

    // WHEN
    val providerFrequencyAccountData = service.getProviderFrequencyAccountByProviderMsaId(
      TestData.MSA_ID
    )

    // THEN
    Assertions.assertThat(providerFrequencyAccountData).isEqualTo(TestData.ProviderFrequencyAccount.DATA)
  }

  @Test
  fun updateProviderFrequencyAccount(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)
    ).thenReturn(mockOrganizationEntity.providerFrequencyAccounts[0])

    // Mock database service `save*` methods
    whenever(organizationDatabaseService.saveProviderApplication(any())).thenReturn(TestData.PROVIDER_APPLICATION_ID)
    whenever(organizationDatabaseService.saveProviderApplicationAsset(any())).thenReturn(TestData.ORGANIZATION_ASSET_ID)
    whenever(organizationDatabaseService.saveProviderApplicationWhitelistedOriginDescriptor(any())).thenReturn(TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID)

    // WHEN
    service.updateProviderFrequencyAccountByProviderMsaId(TestData.MSA_ID, TestData.ProviderFrequencyAccount.DATA)

    // Verify organization entity and related entities are updated/saved
    verify(organizationDatabaseService).updateProviderFrequencyAccount(argThat {
      this.id == TestData.PROVIDER_FREQUENCY_ACCOUNT_ID &&
              this.organizationId == TestData.ORGANIZATION_ID &&
              this.msaId == TestData.MSA_ID
    })

    // Verify that each of the `save*` database service methods for data related to an organization were invoked correctly
    verify(organizationDatabaseService).saveProviderApplication(argThat {
      this.shortCode == TestData.SHORT_CODE &&
          this.providerFrequencyAccountId == TestData.PROVIDER_FREQUENCY_ACCOUNT_ID &&
          this.displayName == TestData.DISPLAY_NAME &&
          this.verifiedCredentialUrl == TestData.VERIFIED_CREDENTIAL_URL &&
          this.id == TestData.PROVIDER_APPLICATION_ID
    })
    verify(organizationDatabaseService).saveProviderApplicationAsset(argThat {
      this.assetType == OrganizationAssetType.BRAND_LOGO &&
          this.url == TestData.BRAND_LOGO_URL &&
          this.providerApplicationId == TestData.PROVIDER_APPLICATION_ID &&
          this.id == TestData.PROVIDER_APPLICATION_ASSET_ID
    })
    verify(organizationDatabaseService).saveProviderApplicationWhitelistedOriginDescriptor(argThat {
      this.providerApplicationId == TestData.PROVIDER_APPLICATION_ID &&
          this.scheme == TestData.WHITELISTED_SCHEME &&
          this.domain == TestData.WHITELISTED_DOMAIN &&
          this.id == TestData.PROVIDER_WHITELISTED_ORIGIN_DESCRIPTOR_ID
    })
  }

  @Test
  fun updateProviderFrequencyAccountWithDeletions(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)
    ).thenReturn(mockOrganizationEntity.providerFrequencyAccounts[0])

    // Mock database service `save*` methods
    whenever(organizationDatabaseService.saveProviderApplication(any())).thenReturn(TestData.PROVIDER_APPLICATION_ID)
    whenever(organizationDatabaseService.saveProviderApplicationAsset(any())).thenReturn(TestData.ORGANIZATION_ASSET_ID)
    whenever(organizationDatabaseService.saveProviderApplicationWhitelistedOriginDescriptor(any())).thenReturn(TestData.WHITELISTED_ORIGIN_DESCRIPTOR_ID)

    // WHEN
    service.updateProviderFrequencyAccountByProviderMsaId(TestData.MSA_ID, TestData.ProviderFrequencyAccount.DATA_NEW)

    // Verify organization entity and related entities are updated/saved
    verify(organizationDatabaseService).updateProviderFrequencyAccount(argThat {
      this.id == TestData.PROVIDER_FREQUENCY_ACCOUNT_ID &&
          this.organizationId == TestData.ORGANIZATION_ID &&
          this.msaId == TestData.MSA_ID
    })

    // Verify related data is deleted
    verify(organizationDatabaseService).deleteProviderApplicationById(eq(TestData.PROVIDER_APPLICATION_ID))
  }

  @Test
  fun deleteProviderApplicationsByProviderFrequencyAccountId(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)
    ).thenReturn(mockOrganizationEntity.providerFrequencyAccounts[0])

    // WHEN
    service.deleteProviderApplicationsByProviderMsaId(TestData.MSA_ID)

    // THEN
    verify(organizationDatabaseService).deleteProviderApplicationById(TestData.PROVIDER_APPLICATION_ID)
  }

  @Test
  fun getProviderApplication(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findProviderApplicationById(TestData.PROVIDER_APPLICATION_ID)
    ).thenReturn(mockProviderApplicationEntity)
    whenever(
      organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)
    ).thenReturn(mockOrganizationEntity.providerFrequencyAccounts[0])

    // WHEN
    val foundProviderApplication = service.getProviderApplication(TestData.MSA_ID, TestData.PROVIDER_APPLICATION_ID)

    Assertions.assertThat(foundProviderApplication).isEqualTo(TestData.ProviderApplication.DATA)
  }

  @Test
  fun saveProviderApplication(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.saveProviderApplication(any())
    ).thenReturn(TestData.PROVIDER_APPLICATION_ID)
    whenever(
      organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)
    ).thenReturn(mockOrganizationEntity.providerFrequencyAccounts[0])

    // WHEN
    service.saveProviderApplication(TestData.MSA_ID, TestData.ProviderApplication.DATA)

    // THEN
    verify(organizationDatabaseService).saveProviderApplication(argThat {
      this.displayName == TestData.DISPLAY_NAME && this.shortCode == TestData.SHORT_CODE
    })

    verifyProviderApplicationDatabaseServiceAncillarySaveMethodsInvoked(false)
  }

  @Test
  fun deleteProviderApplication(): Unit = runBlocking {
    // GIVEN
    whenever(
      organizationDatabaseService.findProviderApplicationById(TestData.PROVIDER_APPLICATION_ID)
    ).thenReturn(mockProviderApplicationEntity)
    whenever(
      organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(TestData.MSA_ID)
    ).thenReturn(mockOrganizationEntity.providerFrequencyAccounts[0])

    // WHEN
    service.deleteProviderApplication(TestData.MSA_ID, TestData.PROVIDER_APPLICATION_ID)

    verify(organizationDatabaseService).deleteProviderApplicationById(TestData.PROVIDER_APPLICATION_ID)
  }
}
package io.amplica.custodial_wallet.service.provider_metadata

import io.amplica.custodial_wallet.OrganizationDatabaseService
import io.amplica.custodial_wallet.db.repository.organization.*
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplication
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationAsset
import io.amplica.custodial_wallet.db.repository.organization.application.ProviderApplicationWhitelistedOriginDescriptor
import io.amplica.custodial_wallet.service.organization.*
import io.amplica.custodial_wallet.service.provider_metadata.DatabaseSourcedProviderMetadataServiceTest.Companion.TestData.ORGANIZATION_ID
import io.amplica.custodial_wallet.service.provider_metadata.DatabaseSourcedProviderMetadataServiceTest.Companion.TestData.PROVIDER_DISPLAY_NAME
import io.amplica.custodial_wallet.service.provider_metadata.DatabaseSourcedProviderMetadataServiceTest.Companion.TestData.PROVIDER_SHORT_NAME
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger
import java.net.URI
import java.time.Instant

class DatabaseSourcedProviderMetadataServiceTest {

  companion object {
    object TestData {
      val PROVIDER_MSA_ID = 28.toBigInteger()
      val ORGANIZATION_ID = 127.toBigInteger()
      const val PROVIDER_DISPLAY_NAME = "Example Provider"
      const val PROVIDER_SHORT_NAME = "exmpl"

      val BRAND_LOGO_ASSET = Asset("example.com/brand-logo.png")
      val BRAND_LOGO_ASSET_ENTITY = OrganizationAsset(
        34.toBigInteger(),
        ORGANIZATION_ID,
        OrganizationAssetType.BRAND_LOGO,
        BRAND_LOGO_ASSET.url,
        Instant.now().toEpochMilli(),
        Instant.now().toEpochMilli(),
        1
      )
      val EXAMPLE_ORIGIN_DESCRIPTOR = OriginDescriptor("https", "example.com")
      val WHITELISTED_ORIGIN_DESCRIPTORS = listOf(
        WhitelistedOriginDescriptor(
          29.toBigInteger(),
          ORGANIZATION_ID,
          EXAMPLE_ORIGIN_DESCRIPTOR.scheme,
          EXAMPLE_ORIGIN_DESCRIPTOR.domain,
          Instant.now().toEpochMilli(),
          Instant.now().toEpochMilli(),
          1
        )
      )

      val ORGANIZATION = Organization(
        ORGANIZATION_ID,
        PROVIDER_DISPLAY_NAME,
        PROVIDER_SHORT_NAME,
        Instant.now().toEpochMilli(),
        Instant.now().toEpochMilli(),
        1
      ).apply {
        this.assets = listOf(BRAND_LOGO_ASSET_ENTITY)
        this.whitelistedOriginDescriptors = WHITELISTED_ORIGIN_DESCRIPTORS
      }
    }
  }

  private val organizationDatabaseService: OrganizationDatabaseService = mock()

  private val service = DatabaseSourcedProviderMetadataService(organizationDatabaseService)

  @Test
  fun resolveProviderMetadata(): Unit = runBlocking {
    // GIVEN
    whenever(organizationDatabaseService.findOneOrganizationByProviderMsaId(eq(TestData.PROVIDER_MSA_ID)))
      .thenReturn(TestData.ORGANIZATION)

    // WHEN
    val metadata = service.resolveProviderMetadata(TestData.PROVIDER_MSA_ID)

    // THEN
    Assertions.assertThat(metadata).usingRecursiveComparison()
      .isEqualTo(
        ProviderMetadata(
          TestData.PROVIDER_DISPLAY_NAME,
          TestData.PROVIDER_SHORT_NAME,
          listOf(TestData.EXAMPLE_ORIGIN_DESCRIPTOR),
          mapOf(AssetType.BRAND_LOGO to TestData.BRAND_LOGO_ASSET),
        )
      )
  }

  @Test
  fun resolveProviderMetadataNoMatch(): Unit = runBlocking {
    // GIVEN
    whenever(organizationDatabaseService.findOneOrganizationByProviderMsaId(eq(TestData.PROVIDER_MSA_ID)))
      .thenReturn(null)

    // WHEN
    val metadata = service.resolveProviderMetadata(TestData.PROVIDER_MSA_ID)

    // THEN
    Assertions.assertThat(metadata).isNull()
  }

  @Test
  fun resolveProviderMetadataForApplication(): Unit = runBlocking {
    // GIVEN
    val msaId = BigInteger.TWO
    val url = URI("www.a.xyz")
    val asset = ProviderApplicationAsset(
      198.toBigInteger(),
      ORGANIZATION_ID,
      OrganizationAssetType.BRAND_LOGO,
      "www.a.xyz/static/logo.tiff",
      Instant.now().toEpochMilli(),
      Instant.now().toEpochMilli(),
      1
    )
    val origin = ProviderApplicationWhitelistedOriginDescriptor(
      23.toBigInteger(),
      ORGANIZATION_ID,
      "https",
      "a.xyz",
      Instant.now().toEpochMilli(),
      Instant.now().toEpochMilli(),
      1
    )
    val providerApplication = ProviderApplication(
      45.toBigInteger(),
      65.toBigInteger(),
      url.toString(),
      PROVIDER_DISPLAY_NAME,
      PROVIDER_SHORT_NAME,
      Instant.now().toEpochMilli(),
      Instant.now().toEpochMilli(),
      1
    ).apply {
      this.providerApplicationAssets = listOf(asset)
      this.providerApplicationWhitelistedOriginDescriptors = listOf(origin)
    }

    whenever(organizationDatabaseService.findProviderApplicationByUrl(eq(msaId), eq(url)))
      .thenReturn(providerApplication)

    // WHEN
    val metadata = service.resolveProviderMetadataForApplication(msaId, url)

    // THEN
    Assertions.assertThat(metadata).usingRecursiveComparison()
      .isEqualTo(
        ProviderMetadata(
          PROVIDER_DISPLAY_NAME,
          PROVIDER_SHORT_NAME,
          listOf(origin.toProviderApplicationOriginDescriptor()),
          mapOf(AssetType.BRAND_LOGO to Asset(asset.url)),
        )
      )
  }

  @Test
  fun resolveProviderMetadataForApplicationNoMatch(): Unit = runBlocking {
    // GIVEN
    whenever(organizationDatabaseService.findProviderApplicationByUrl(any(), any()))
      .thenReturn(null)

    // WHEN
    val metadata = service.resolveProviderMetadataForApplication(BigInteger.ONE, URI("www.not-real.com"))

    // THEN
    Assertions.assertThat(metadata).isNull()
  }

}
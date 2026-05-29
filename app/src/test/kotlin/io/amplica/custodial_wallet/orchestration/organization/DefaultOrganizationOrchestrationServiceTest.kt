package io.amplica.custodial_wallet.orchestration.organization

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import com.strategyobject.substrateclient.crypto.ss58.SS58Codec
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.service.organization.*
import io.amplica.custodial_wallet.template.TemplateConstants
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.PublicKeyFormat
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigInteger
import java.nio.charset.StandardCharsets


class DefaultOrganizationOrchestrationServiceTest {

  private val hostName = "www.example.com"
  private val mockOrganizationService: OrganizationService = mock()
  private val mockLookupOrchestrationService: LookupOrchestrationService = mock()

  private val service = DefaultOrganizationOrchestrationService(
    hostName,
    mockOrganizationService,
    mockLookupOrchestrationService,
  )

  @Nested
  inner class OrganizationPublicKeyTests {
    private val publicKeyBytes = "This public key will be 32bytes!".toByteArray(StandardCharsets.US_ASCII)
    private val publicKey = PublicKeyDto(
      SS58Codec.encode(publicKeyBytes, SS58AddressFormat.SUBSTRATE_ACCOUNT),
      Encoding.BASE_58,
      PublicKeyFormat.SS58,
      KeyPairType.SR25519
    )

    private val msaId = 34.toBigInteger()
    private val providerName = "Test Provider"

    private fun getExpectedDefaultData(msaId: BigInteger, providerName: String): OrganizationData {
      val expectedShortCode = TemplateConstants.DEFAULT_PROVIDER_NAME
      val expectedBrandLogo = "$hostName/img/providers/swif_example_app_brand_logo.png"
      return OrganizationData(
        setOf(msaId),
        providerName,
        expectedShortCode,
        emptyList(),
        mapOf(
          AssetType.BRAND_LOGO to Asset(expectedBrandLogo)
        )
      )
    }

    @Test
    fun saveOrUpdateOrganizationFromPublicKey(): Unit = runBlocking {
      // GIVEN
      whenever(mockLookupOrchestrationService.retrieveMsaId(eq(publicKey))).thenReturn(msaId)
      whenever(mockLookupOrchestrationService.getProviderName(eq(msaId))).thenReturn(providerName)

      // WHEN
      val organizationData = service.saveOrUpdateOrganizationFromPublicKey(publicKey)

      // THEN
      verify(mockOrganizationService, times(1)).getOrganizationByMsaId(eq(msaId))

      val expectedOrganizationData = getExpectedDefaultData(msaId, providerName)
      verify(mockOrganizationService, times(1)).saveOrganization(eq(expectedOrganizationData))
      Assertions.assertThat(organizationData).isEqualTo(expectedOrganizationData)
    }

    @Test
    fun saveOrUpdateOrganizationFromPublicKeyUpdate(): Unit = runBlocking {
      // GIVEN
      val existingId = 43.toBigInteger()
      val existingData = OrganizationData(
        setOf(msaId),
        "Example Provider", // Different from `providerName`
        "example",
        listOf(OriginDescriptor("https", "www.example.com")),
        mapOf(
          AssetType.BRAND_LOGO to Asset("www.example.com/logo.png")
        )
      )
      whenever(mockOrganizationService.getOrganizationByMsaId(eq(msaId))).thenReturn(
        Pair(existingId, existingData)
      )

      whenever(mockLookupOrchestrationService.retrieveMsaId(eq(publicKey))).thenReturn(msaId)
      whenever(mockLookupOrchestrationService.getProviderName(eq(msaId))).thenReturn(providerName)

      // WHEN
      val organizationData = service.saveOrUpdateOrganizationFromPublicKey(publicKey)

      // THEN
      verify(mockOrganizationService, times(1)).getOrganizationByMsaId(eq(msaId))

      // Assert only the provider's name is updated
      val expectedOrganizationData = existingData.copy(displayName = providerName)
      verify(mockOrganizationService, times(1)).updateOrganization(
        eq(existingId), eq(expectedOrganizationData)
      )
      Assertions.assertThat(organizationData).isEqualTo(expectedOrganizationData)
    }
  }

}

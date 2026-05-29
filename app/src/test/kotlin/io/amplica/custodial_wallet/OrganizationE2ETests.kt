package io.amplica.custodial_wallet

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import com.strategyobject.substrateclient.crypto.ss58.SS58Codec
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.container.CustodialWalletE2ETestStack
import io.amplica.custodial_wallet.db.repository.organization.ReactiveProviderFrequencyAccountRepository
import io.amplica.custodial_wallet.dto.OrganizationDataBody
import io.amplica.custodial_wallet.dto.ProviderApplicationDataBody
import io.amplica.custodial_wallet.dto.ProviderFrequencyAccountDataBody
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiErrorDto
import io.amplica.custodial_wallet.service.organization.*
import io.amplica.custodial_wallet.template.TemplateConstants
import io.amplica.custodial_wallet.util.CustodialWalletE2ESpringTestConfiguration
import io.amplica.custodial_wallet.util.DbUtil
import io.amplica.custodial_wallet.util.createDefaultHttpHeaders
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.util.key_creation.PublicKeyFormat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.boot.web.client.RestTemplateCustomizer
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.RestClientException
import org.testcontainers.junit.jupiter.Container
import java.math.BigInteger
import java.net.URI
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions as Assertj


@CustodialWalletE2ESpringTestConfiguration
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class OrganizationE2ETests(
  @Qualifier(BeanNames.DB_UTIL) private val dbUtil: DbUtil,
) {

  @Autowired
  lateinit var providerFrequencyAccountRepository: ReactiveProviderFrequencyAccountRepository

  @Autowired
  lateinit var testRestTemplate: TestRestTemplate

  @Value("\${unfinished.custodial-wallet.admin.shared.secret}")
  private lateinit var sharedSecret: String

  companion object {

    private const val SHARED_SECRET_PARAMETER_NAME = "shared_secret"

    @Container
    val containers = CustodialWalletE2ETestStack()

    @DynamicPropertySource
    @JvmStatic
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      containers.registerDynamicProperties(registry)
    }
    
  }

  @BeforeEach
  fun setup() {
    /*
    This is needed to have the same setup as Spring Boot 3.3.9, otherwise the test needs to change, and it wasn't at all
    clear to me why, it's like a strange quirk of the SimpleClientHttpRequestFactory 'cause I'd say the ReactorClientHttpRequestFactory
    is working correct in that it will ALWAYS redirect and for whatever reason the SimpleClientHttpRequestFactory doesn't redirect for the
    givenProperBatchPayloadToSignRequest_whenBatchSigningRequest_BatchSuccessfullySigned test, but it does for others...
     */
    val simpleClientHttpRequestFactory = ClientHttpRequestFactoryBuilder.of(
      SimpleClientHttpRequestFactory::class.java
    ).build()
    val restTemplateBuilder = (RestTemplateBuilder(RestTemplateCustomizer {
      it.requestFactory = simpleClientHttpRequestFactory
    }).rootUri(testRestTemplate.rootUri))
    testRestTemplate = TestRestTemplate(restTemplateBuilder)
  }

  @AfterEach
  fun tearDown() {
    dbUtil.deleteFromAllTables()
  }

  @Nested
  @DisplayName("Organization API Tests")
  inner class OrganizationTests {
    private val msaId = 57.toBigInteger()
    private val initialOrganizationDataBody = OrganizationDataBody(
      listOf(msaId),
      "Mohave Posting Co.",
      "mohave",
      listOf(OriginDescriptor("https", "mohave.co")),
      mapOf(AssetType.BRAND_LOGO to Asset("https://s3.us-east-1.aws/mohave-static-assets/brand-logo.png"))
    )
    
    private val providerApplicationDataBody = ProviderApplicationDataBody(
      URI("https://notFoundIndustry.co"),
      "404 Industries Co.",
      "404",
      listOf(OriginDescriptor("https", "notFoundIndustry.co")),
      mapOf(AssetType.BRAND_LOGO to Asset("https://s3.us-east-1.aws/404-industry-assets/brand-logo.png"))
    )

    private val saveOrganizationUrl = "/api/admin/organization?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
    
    private val saveProviderApplicationUrl = "/api/admin/provider/msa/$msaId/applications?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"

    private fun assertOrganizationDataEqual(requestBody: OrganizationDataBody, actualData: OrganizationData) {
      Assertions.assertTrue(requestBody.msaIds.toSet() == actualData.msaIds)
      Assertions.assertEquals(requestBody.displayName, actualData.displayName)
      Assertions.assertEquals(requestBody.shortcode, actualData.shortcode)
      Assertions.assertTrue(requestBody.whitelistedOrigins.toSet() == actualData.whitelistedOrigins.toSet())
      Assertions.assertTrue(requestBody.assets == actualData.assets)
    }

    private fun assertProviderFrequencyAccountDataEqual(requestBody: ProviderFrequencyAccountDataBody, actualData: ProviderFrequencyAccountData) {
      Assertions.assertTrue(requestBody.organizationId == actualData.organizationId)
      assertProviderApplicationDataEqual(requestBody.providerApplications, actualData.providerApplications)
    }

    private fun assertProviderApplicationDataEqual(requestBody: List<ProviderApplicationDataBody>, actualData: List<ProviderApplicationData>){
      for (item in requestBody){
        var foundApplication = false
        for (application in actualData){
          foundApplication = true
          if(item.verifiedCredentialUrl == application.verifiedCredentialUrl){
            Assertions.assertEquals(item.displayName, application.displayName)
            Assertions.assertEquals(item.shortcode, application.shortcode)
            Assertions.assertTrue(item.whitelistedOrigins.toSet() == application.whitelistedOrigins.toSet())
            Assertions.assertTrue(item.assets == application.assets)
          }
        }
        Assertions.assertEquals(foundApplication, true)
      }
    }

    private fun getOrganizationData(organizationId: BigInteger): OrganizationData = runBlocking {
      val url = "/api/admin/organization/$organizationId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
      val response = testRestTemplate.getForEntity(url, OrganizationData::class.java, parameters)

      response.body ?: Assertions.fail("Body is null")
    }

    private fun getProviderApplicationData(providerApplicationId: BigInteger):  ResponseEntity<ProviderApplicationData> = runBlocking {
      val url = "/api/admin/provider/msa/$msaId/applications/$providerApplicationId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
      testRestTemplate.getForEntity(url, ProviderApplicationData::class.java, parameters)
    }

    @Test
    fun saveOrganization(): Unit = runBlocking {
      // GIVEN
      val request = HttpEntity(initialOrganizationDataBody, createDefaultHttpHeaders())
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)

      // WHEN
      val response = testRestTemplate.postForEntity(saveOrganizationUrl, request, String::class.java, parameters)

      // THEN
      Assertions.assertTrue(response.statusCode.is2xxSuccessful)
      val location = response.headers.location ?: Assertions.fail("`response` Location header is null")
      val organizationId = location.path.split("/").last().toBigInteger()

      val savedData = getOrganizationData(organizationId)
      assertOrganizationDataEqual(initialOrganizationDataBody, savedData)
    }

    @Test
    fun saveOrganizationInvalidSharedSecret(): Unit = runBlocking {
      // GIVEN
      val request = HttpEntity(initialOrganizationDataBody, createDefaultHttpHeaders())
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to "invalidSharedSecret")

      // WHEN
      val response = testRestTemplate.postForEntity(saveOrganizationUrl, request, String::class.java, parameters)

      // THEN
      Assertions.assertTrue(response.statusCode.is4xxClientError)
      Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
    }

    @ParameterizedTest
    @CsvSource(
      delimiter = '|', // Ignore the commas in the JSON
      value =  [
        """{"msaIds":[],"displayName":"Mohave Posting Co.","shortcode":"mohave","whitelistedOrigins":[{"scheme":"https","domain":"mohave.co"}],"assets":{}}""",
        """{"msaIds":[57],"displayName":"Mohave Posting Co.","shortcode":"mohave","whitelistedOrigins":[],"assets":{}}""",
      ]
    )
    fun saveOrganizationInvalidBody(jsonBody: String): Unit = runBlocking {
      // GIVEN
      val body = jacksonObjectMapper().readValue(jsonBody, OrganizationDataBody::class.java)
      val request = HttpEntity(body, createDefaultHttpHeaders())
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)

      // WHEN
      val response = testRestTemplate.postForEntity(saveOrganizationUrl, request, ApiErrorDto::class.java, parameters)

      // THEN
      Assertions.assertTrue(response.statusCode.is4xxClientError)
      Assertions.assertEquals(ApiError.EMPTY_REQUIRED_LIST_ERROR.id, response.body?.id)
      Assertions.assertEquals(ApiError.EMPTY_REQUIRED_LIST_ERROR.description, response.body?.description)
    }

    private fun saveInitialOrganization(body: OrganizationDataBody): BigInteger = runBlocking {
      val headers = createDefaultHttpHeaders()
      val request = HttpEntity(body, headers)
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
      val response = testRestTemplate.postForEntity(saveOrganizationUrl, request, String::class.java, parameters)
      val location = response.headers.location ?: Assertions.fail("`response` Location header is null")
      val organizationId = location.path.split("/").last().toBigInteger()

      organizationId
    }

    private fun createUpdateProviderFrequencyAccountDataBody(organizationId: BigInteger): ProviderFrequencyAccountDataBody {
      return ProviderFrequencyAccountDataBody(
        organizationId,
        listOf(
          ProviderApplicationDataBody(
            URI.create("https://example.com"),
            "Mohave Posting Co.",
            "mohave",
            listOf(OriginDescriptor("https", "mohave.co")),
            mapOf()
          )
        )
      )
    }

    @ParameterizedTest
    @CsvSource(
      delimiter = '|',
      value =  [
        // Empty list of MSA IDs (expecting error)
        """{"msaIds":[],"displayName":"Mohave Posting Co.","shortcode":"mohave","whitelistedOrigins":[{"scheme":"https","domain":"mohave.co"}],"assets":{}} | 7""",
        // Empty list of whitelisted origins (expecting error)
        """{"msaIds":[57],"displayName":"Mohave Posting Co.","shortcode":"mohave","whitelistedOrigins":[],"assets":{}} | 7""",
        // Removing shortcode
        """{"msaIds":[57],"displayName":"Mohave Posting Co.","whitelistedOrigins":[{"scheme":"https","domain":"mohave.co"}],"assets":{}} | """,
        // Adding MSA IDs
        """{"msaIds":[57, 72, 73],"displayName":"Mohave Posting Co.","shortcode":"mohave","whitelistedOrigins":[{"scheme":"https","domain":"mohave.co"}],"assets":{}} | """,
        // Adding origins
        """{"msaIds":[57],"displayName":"Mohave Posting Co.","shortcode":"mohave","whitelistedOrigins":[{"scheme":"https","domain":"mohave.co"}, {"scheme":"https","domain":"qa-mohave.xyz"}],"assets":{}} | """,
        // Renaming and re-branding
        """{"msaIds":[57],"displayName":"Project Mohave Labs","shortcode":"mohave","whitelistedOrigins":[{"scheme":"https","domain":"mohave.co"}],"assets":{"BRAND_LOGO":{"url":"https://s3.us-east-1.aws/mohave-static-assets/project-mohave-updated-logo.png"}}} | """,
      ]
    )
    fun updateOrganization(jsonBody: String, expectedApiErrorId: Int?) {
      // GIVEN
      val organizationId = saveInitialOrganization(initialOrganizationDataBody)

      val updateBody = jacksonObjectMapper().readValue(jsonBody, OrganizationDataBody::class.java)
      val request = HttpEntity(updateBody, createDefaultHttpHeaders())
      val updateUrl = "/api/admin/organization/$organizationId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)

      when (expectedApiErrorId) {
        null -> {
          // WHEN
          val response = testRestTemplate.exchange(updateUrl, HttpMethod.PUT, request, String::class.java, parameters)

          // THEN
          Assertions.assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode.value())

          val expectedData = when {
            // `shortcode` should never be stored as the empty string or null
            updateBody.shortcode.isNullOrEmpty() -> updateBody.copy(shortcode = TemplateConstants.DEFAULT_PROVIDER_NAME)
            else -> updateBody
          }
          val actualData = getOrganizationData(organizationId)
          assertOrganizationDataEqual(expectedData, actualData)
        }

        else -> {
          // WHEN
          val errorResponse = testRestTemplate.exchange(updateUrl, HttpMethod.PUT, request, ApiErrorDto::class.java, parameters)

          // THEN
          val expectedApiError = ApiError.fromId(expectedApiErrorId)

          Assertions.assertEquals(expectedApiError.httpStatus.value(), errorResponse.statusCode.value())
          Assertions.assertEquals(expectedApiError.id, errorResponse.body?.id)
          Assertions.assertEquals(expectedApiError.description, errorResponse.body?.description)
        }
      }
    }

    @Test
    fun updateOrganizationInvalidSharedSecret(): Unit = runBlocking {
      // GIVEN
      val organizationId = saveInitialOrganization(initialOrganizationDataBody)

      val request = HttpEntity(initialOrganizationDataBody, createDefaultHttpHeaders())
      val updateUrl = "/api/admin/organization/$organizationId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to "invalidSharedSecret")

      // WHEN
      val response = testRestTemplate.exchange(updateUrl, HttpMethod.PUT, request, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
    }

    @ParameterizedTest
    @CsvSource(value = ["true", "false"])
    fun upsertOrganizationFromPublicKey(isUpdate: Boolean): Unit = runBlocking {
      // GIVEN
      val url = "/api/admin/organization/publicKey"
      val body = containers.frequency.aliceProviderPublicKeyDto
      val request = HttpEntity(body, createDefaultHttpHeaders())

      if (isUpdate) {
        // Make an initial request to create the organization
        val response = testRestTemplate.postForEntity(url, request, String::class.java)
        Assertions.assertTrue(response.statusCode.is2xxSuccessful)
      }

      // WHEN
      val response = testRestTemplate.postForEntity(url, request, String::class.java)

      // THEN
      Assertj.assertThat(response.statusCode.is2xxSuccessful).isTrue()
      val responseBody = jacksonObjectMapper().readValue(response.body, OrganizationData::class.java)
      val expected = OrganizationData(
        setOf(1.toBigInteger()),
        "Alice Provider",
        "und",
        emptyList(),
        mapOf(AssetType.BRAND_LOGO to Asset("http://localhost:8080/img/providers/swif_example_app_brand_logo.png"))
      )
      Assertj.assertThat(responseBody).isEqualTo(expected)
    }

    @Test
    fun upsertOrganizationFromPublicKeyNotDefined(): Unit = runBlocking {
      // GIVEN
      val url = "/api/admin/organization/publicKey"
      val publicKeyBytes = "This public key will be 32bytes!".toByteArray(StandardCharsets.US_ASCII)
      val body = PublicKeyDto(
        SS58Codec.encode(publicKeyBytes, SS58AddressFormat.SUBSTRATE_ACCOUNT),
        Encoding.BASE_58,
        PublicKeyFormat.SS58,
        KeyPairType.SR25519
      )
      val request = HttpEntity(body, createDefaultHttpHeaders())

      // WHEN
      val response = testRestTemplate.postForEntity(url, request, ApiErrorDto::class.java)

      // THEN
      Assertj.assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
      Assertj.assertThat(response.body?.id).isEqualTo(ApiError.NO_MSA_ID_FOUND_ERROR.id)
      Assertj.assertThat(response.body?.description).isEqualTo(ApiError.NO_MSA_ID_FOUND_ERROR.description)
    }

    @Test
    fun deleteOrganizationProviderFrequencyAccounts() {
      // GIVEN
      val organizationId = saveInitialOrganization(initialOrganizationDataBody)
      val url = "/api/admin/organization/$organizationId/providerFrequencyAccounts?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)

      // WHEN
      val response = testRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode.value())

      // Expect msaIds list to be empty
      val expectedData = initialOrganizationDataBody.copy(msaIds = emptyList())
      val actualData = getOrganizationData(organizationId)
      assertOrganizationDataEqual(expectedData, actualData)
    }

    @Test
    fun deleteOrganizationProviderFrequencyAccountsInvalidSharedSecret() {
      // GIVEN
      val organizationId = saveInitialOrganization(initialOrganizationDataBody)
      val url = "/api/admin/organization/$organizationId/providerFrequencyAccounts?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to "invalidSharedSecret")

      // WHEN
      val response = testRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
    }

    private fun getProviderFrequencyAccountDataByMsaId(providerMsaId: BigInteger): ProviderFrequencyAccountData {
      val url = "/api/admin/provider/msa/$providerMsaId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
      val response = testRestTemplate.getForEntity(url, ProviderFrequencyAccountData::class.java, parameters)

      return response.body ?: Assertions.fail("Body is null")
    }

    @Test
    fun updateProviderFrequencyAccount() {
      // GIVEN
      val organizationId = saveInitialOrganization(initialOrganizationDataBody)
      val providerMsaId = providerFrequencyAccountRepository.findAll().collectList().block()!!.filter { a-> a.organizationId == organizationId }[0].msaId
      val updateBody = createUpdateProviderFrequencyAccountDataBody(organizationId)
      val request = HttpEntity(updateBody, createDefaultHttpHeaders())
      val updateUrl = "/api/admin/provider/msa/$providerMsaId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)

      //WHEN
      val response = testRestTemplate.exchange(updateUrl, HttpMethod.PUT, request, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode.value())
      val actualData = getProviderFrequencyAccountDataByMsaId(providerMsaId)
      assertProviderFrequencyAccountDataEqual(updateBody, actualData)
    }

    @Test
    fun updateProviderFrequencyAccountInvalidSharedSecret() {
      // GIVEN
      val organizationId = saveInitialOrganization(initialOrganizationDataBody)
      val providerMsaId = providerFrequencyAccountRepository.findAll().collectList().block()!!.filter { a-> a.organizationId == organizationId }[0].msaId
      val updateBody = createUpdateProviderFrequencyAccountDataBody(organizationId)
      val request = HttpEntity(updateBody, createDefaultHttpHeaders())
      val updateUrl = "/api/admin/provider/msa/$providerMsaId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to "invalidSharedSecret")

      //WHEN
      val response = testRestTemplate.exchange(updateUrl, HttpMethod.PUT, request, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
    }

    @Test
    fun deleteProviderFrequencyAccountApplications() {
      // GIVEN
      val organizationId = saveInitialOrganization(initialOrganizationDataBody)
      val providerMsaId = providerFrequencyAccountRepository.findAll().collectList().block()!!.filter { a-> a.organizationId == organizationId }[0].msaId
      val initialProviderFrequencyAccountData = getProviderFrequencyAccountDataByMsaId(providerMsaId)
      val url = "/api/admin/provider/msa/$providerMsaId/applications?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)

      // WHEN
      val response = testRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode.value())

      // Expect providerApplications list to be empty
      val expectedData = initialProviderFrequencyAccountData.copy(providerApplications = emptyList())
      val actualData = getProviderFrequencyAccountDataByMsaId(providerMsaId)
      Assertions.assertEquals(expectedData, actualData)
    }

    @Test
    fun deleteProviderFrequencyAccountApplicationsInvalidSharedSecret() {
      // GIVEN
      val organizationId = saveInitialOrganization(initialOrganizationDataBody)
      val providerMsaId = providerFrequencyAccountRepository.findAll().collectList().block()!!.filter { a-> a.organizationId == organizationId }[0].msaId
      val url = "/api/admin/provider/msa/$providerMsaId/applications?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to "invalidSharedSecret")

      // WHEN
      val response = testRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
    }

    @Test
    fun saveProviderApplication(): Unit = runBlocking {
      // GIVEN
      saveInitialOrganization(initialOrganizationDataBody)
      val request = HttpEntity(providerApplicationDataBody, createDefaultHttpHeaders())
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)

      // WHEN
      val response = testRestTemplate.postForEntity(saveProviderApplicationUrl, request, String::class.java, parameters)

      // THEN
      Assertions.assertTrue(response.statusCode.is2xxSuccessful)
      val location = response.headers.location ?: Assertions.fail("`response` Location header is null")
      val providerApplicationId = location.path.split("/").last().toBigInteger()

      val savedData = getProviderApplicationData(providerApplicationId).body
      assertProviderApplicationDataEqual(listOf(providerApplicationDataBody), listOf(savedData!!))
    }

    @Test
    fun saveProviderApplicationInvalidSharedSecret(): Unit = runBlocking {
      // GIVEN
      saveInitialOrganization(initialOrganizationDataBody)
      val request = HttpEntity(providerApplicationDataBody, createDefaultHttpHeaders())
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to "invalidSharedSecret")

      // WHEN
      val response = testRestTemplate.postForEntity(saveProviderApplicationUrl, request, String::class.java, parameters)

      // THEN
      Assertions.assertTrue(response.statusCode.is4xxClientError)
      Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
    }

    private fun saveInitialProviderApplication(body: ProviderApplicationDataBody): BigInteger = runBlocking {
      saveInitialOrganization(initialOrganizationDataBody)
      val headers = createDefaultHttpHeaders()
      val request = HttpEntity(body, headers)
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)
      val response = testRestTemplate.postForEntity(saveProviderApplicationUrl, request, String::class.java, parameters)
      val location = response.headers.location ?: Assertions.fail("`response` Location header is null")
      val providerApplicationId = location.path.split("/").last().toBigInteger()
      providerApplicationId
    }

    @Test
    fun deleteProviderApplication() {
      // GIVEN
      val providerApplicationId = saveInitialProviderApplication(providerApplicationDataBody)
      val url = "/api/admin/provider/msa/$msaId/applications/$providerApplicationId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to sharedSecret)

      // WHEN
      val response = testRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode.value())

      Assertions.assertThrows(RestClientException::class.java) { getProviderApplicationData(providerApplicationId) }

    }

    @Test
    fun deleteProviderApplicationInvalidSharedSecret() {
      // GIVEN
      val providerApplicationId = saveInitialProviderApplication(providerApplicationDataBody)
      val url = "/api/admin/provider/msa/$msaId/applications/$providerApplicationId?$SHARED_SECRET_PARAMETER_NAME={$SHARED_SECRET_PARAMETER_NAME}"
      val parameters = mapOf(SHARED_SECRET_PARAMETER_NAME to "invalidSharedSecret")

      // WHEN
      val response = testRestTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java, parameters)

      // THEN
      Assertions.assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode.value())
    }
  }
}

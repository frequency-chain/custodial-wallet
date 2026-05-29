package io.amplica.custodial_wallet

import io.amplica.custodial_wallet.db.conf.ReactiveCustodialWalletDatabaseServiceConf
import io.amplica.custodial_wallet.db.repository.organization.*
import io.amplica.custodial_wallet.db.repository.organization.application.*
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigInteger
import java.net.URI


@Testcontainers
@SpringJUnitConfig(classes = [ReactiveCustodialWalletDatabaseServiceConf::class])
@EnableAutoConfiguration
class ReactiveOrganizationDatabaseServiceTest {

  companion object {
    object TestData {
      object Provider {
        val MSA_ID = 42.toBigInteger()
      }

      object Organization {
        const val DISPLAY_NAME = "Example Provider"
        const val SHORT_CODE = "exmpl"
      }

      object AlternateOrganization {
        const val DISPLAY_NAME = "Alternate Provider"
        const val SHORT_CODE = "alt"
      }

      object Origin {
        val HTTPS = Pair("https", "example.com")
        val OTHER_SCHEME = Pair("myscheme", "example")
      }

      object Asset {
        const val BRAND_LOGO_URL = "example.com/brand-logo.png"
      }

      object ProviderApplication {
        const val VERIFIED_CREDENTIAL_URL = "providerapplciation.com"
        const val DISPLAY_NAME = "Example Provider Application"
        const val SHORT_CODE = "exapp"
      }

      object ApplicationOrigin {
        val HTTPS = Pair("https", "exampleapp.com")
        val OTHER_SCHEME = Pair("myscheme", "exampleapp")
      }

      object ApplicationAsset {
        const val BRAND_LOGO_URL = "exampleapp.com/brand-logo.png"
      }
    }

    private const val USERNAME = "unfinished"
    private const val PASSWORD = "somePassword"
    private const val DATABASE_NAME = "custodial_wallet_dev"
    private const val SCHEMA = "custodial_wallet"

    // This is not marked @Container by design and handled by hand because the .apply trick in order to use the
    // Testcontainers builder doesn't resolve correctly when setting module things like .withXXX
    private lateinit var postgres: PostgreSQLContainer<Nothing>

    @DynamicPropertySource
    @JvmStatic
    fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
      registry.add("unfinished.custodial-wallet.db.service.createSchema") { true }
      registry.add("unfinished.custodial-wallet.db.service.schema") { SCHEMA }
      registry.add("spring.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${DATABASE_NAME}?schema=${SCHEMA}" }
      registry.add("unfinished.custodial-wallet.ro.r2dbc.url") { "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${DATABASE_NAME}?schema=${SCHEMA}" }
      registry.add("spring.r2dbc.username") { USERNAME }
      registry.add("spring.r2dbc.password") { PASSWORD }
      registry.add("unfinished.custodial-wallet.r2dbc.statement.timeout.millis") { 5000 }

    }

    @BeforeAll
    @JvmStatic
    fun setUpClass() {
      postgres = PostgreSQLContainer<Nothing>("postgres:12.7")
      postgres.withUsername(USERNAME)
      postgres.withPassword(PASSWORD)
      postgres.withDatabaseName(DATABASE_NAME)
      postgres.addEnv("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --lc-collate=en_US.UTF-8 --lc-ctype=en_US.UTF-8")
      postgres.start()
    }

    @AfterAll
    @JvmStatic
    fun afterClass() {
      postgres.stop()
    }
  }

  @Autowired
  lateinit var organizationDatabaseService: OrganizationDatabaseService

  @Autowired
  lateinit var organizationRepository: ReactiveOrganizationRepository

  @Autowired
  lateinit var organizationAssetRepository: ReactiveOrganizationAssetRepository

  @Autowired
  lateinit var providerFrequencyAccountRepository: ReactiveProviderFrequencyAccountRepository

  @Autowired
  lateinit var whitelistedOriginDescriptorRepository: ReactiveWhitelistedOriginDescriptorRepository

  @Autowired
  lateinit var providerApplicationRepository: ReactiveProviderApplicationRepository

  @Autowired
  lateinit var providerApplicationAssetRepository: ReactiveProviderApplicationAssetRepository

  @Autowired
  lateinit var providerApplicationWhitelistedOriginDescriptorRepository: ReactiveProviderApplicationWhitelistedOriginDescriptorRepository

  @AfterEach
  fun tearDown() {
    runBlocking {
      providerApplicationWhitelistedOriginDescriptorRepository.deleteAll().awaitFirstOrNull()
      providerApplicationAssetRepository.deleteAll().awaitFirstOrNull()
      providerApplicationRepository.deleteAll().awaitFirstOrNull()
      whitelistedOriginDescriptorRepository.deleteAll().awaitFirstOrNull()
      providerFrequencyAccountRepository.deleteAll().awaitFirstOrNull()
      organizationAssetRepository.deleteAll().awaitFirstOrNull()
      organizationRepository.deleteAll().awaitFirstOrNull()

    }
  }

  @Test
  fun saveOrganization(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)

    // WHEN
    val id = organizationDatabaseService.saveOrganization(organization)

    // THEN
    val result = organizationRepository.findById(id).awaitSingle()

    Assertions.assertThat(result).usingRecursiveComparison().ignoringFields("id", "version").isEqualTo(organization)
    Assertions.assertThat(result.version).isEqualTo(0)
  }

  @Test
  fun updateOrganization(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)
    val id = organizationDatabaseService.saveOrganization(organization)
    val savedOrganization = organization.copy(id = id)

    val updatedOrganization = savedOrganization.copy(
      displayName = TestData.AlternateOrganization.DISPLAY_NAME,
      shortCode = TestData.AlternateOrganization.SHORT_CODE
    )

    // WHEN
    organizationDatabaseService.updateOrganization(updatedOrganization)

    // THEN
    val result = organizationRepository.findById(id).awaitSingle()

    Assertions.assertThat(result.displayName).isEqualTo(TestData.AlternateOrganization.DISPLAY_NAME)
    Assertions.assertThat(result.shortCode).isEqualTo(TestData.AlternateOrganization.SHORT_CODE)
    Assertions.assertThat(result.createdAt).isEqualTo(organization.createdAt)
    Assertions.assertThat(result.lastModified).isGreaterThan(organization.lastModified)
    Assertions.assertThat(result.version).isEqualTo(1)
  }

  @Test
  fun findOneOrganizationById(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)
    val savedOrganization = organizationRepository.save(organization).awaitSingle()
    val id = savedOrganization.id!!

    // WHEN
    val result = organizationDatabaseService.findOneOrganizationById(id) ?: Assertions.fail("Organization is null")

    // THEN
    Assertions.assertThat(result.displayName).isEqualTo(TestData.Organization.DISPLAY_NAME)
    Assertions.assertThat(result.shortCode).isEqualTo(TestData.Organization.SHORT_CODE)
    Assertions.assertThat(result.version).isEqualTo(0)
  }

  @Test
  fun findOneOrganizationByIdNotFound(): Unit = runBlocking {
    // GIVEN
    // (nothing)

    // WHEN
    val result = organizationDatabaseService.findOneOrganizationById(0.toBigInteger())

    // THEN
    Assertions.assertThat(result).isNull()
  }

  @Test
  fun findOneOrganizationByProviderMsaId(): Unit = runBlocking {
    // GIVEN
    val savedOrganization = organizationRepository.save(
      Organization.create(
        TestData.Organization.DISPLAY_NAME,
        TestData.Organization.SHORT_CODE,
      )
    ).awaitSingle()
    providerFrequencyAccountRepository.save(
      ProviderFrequencyAccount.create(
        TestData.Provider.MSA_ID,
        savedOrganization.id!!,
      )
    ).awaitSingle()
    val descriptors = listOf(TestData.Origin.HTTPS, TestData.Origin.OTHER_SCHEME).map { (scheme, domain) ->
      WhitelistedOriginDescriptor.create(
        savedOrganization.id!!,
        scheme,
        domain,
      )
    }
    descriptors.forEach { descriptor ->
      whitelistedOriginDescriptorRepository.save(descriptor).awaitSingle()
    }
    organizationAssetRepository.save(
      OrganizationAsset.create(
        savedOrganization.id!!,
        OrganizationAssetType.BRAND_LOGO,
        TestData.Asset.BRAND_LOGO_URL,
      )
    ).awaitSingle()

    // WHEN
    val organization = organizationDatabaseService.findOneOrganizationByProviderMsaId(TestData.Provider.MSA_ID)

    // THEN
    Assertions.assertThat(organization!!.displayName).isEqualTo(TestData.Organization.DISPLAY_NAME)
    Assertions.assertThat(organization.shortCode).isEqualTo(TestData.Organization.SHORT_CODE)

    Assertions.assertThat(organization.assets).hasSize(1)
    Assertions.assertThat(organization.assets.first()).usingRecursiveComparison().comparingOnlyFields("assetType", "url").isEqualTo(
      OrganizationAsset.create(
        savedOrganization.id!!,
        OrganizationAssetType.BRAND_LOGO,
        TestData.Asset.BRAND_LOGO_URL,
      )
    )

    Assertions.assertThat(organization.whitelistedOriginDescriptors).hasSize(2)
    listOf(TestData.Origin.HTTPS, TestData.Origin.OTHER_SCHEME).forEach { (scheme, domain) ->
      Assertions.assertThat(organization.whitelistedOriginDescriptors).anyMatch { it.scheme == scheme && it.domain == domain }
    }

    Assertions.assertThat(organization.providerFrequencyAccounts).hasSize(1)
  }

  @Test
  fun findOneOrganizationByProviderMsaIdNotFound(): Unit = runBlocking {
    // GIVEN
    // (nothing)

    // WHEN
    val organization = organizationDatabaseService.findOneOrganizationByProviderMsaId(TestData.Provider.MSA_ID)

    // THEN
    Assertions.assertThat(organization).isNull()
  }

  @Test
  fun deleteAllProviderFrequencyAccountsByOrganizationId(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)
    val savedOrganization = organizationRepository.save(organization).awaitSingle()
    val organizationId = savedOrganization.id!!

    val msaIds = listOf(1,2,3).map { it.toBigInteger() }
    msaIds.forEach { msaId ->
      providerFrequencyAccountRepository.save(ProviderFrequencyAccount.create(msaId, organizationId))
    }

    // WHEN
    organizationDatabaseService.deleteAllProviderFrequencyAccountsByOrganizationId(organizationId)

    // THEN
    val providerAccounts = providerFrequencyAccountRepository.findAll().collectList().awaitSingle()
    Assertions.assertThat(providerAccounts).isEmpty()
  }

  @Test
  fun deleteAllProviderFrequencyAccountsByOrganizationIdNoEntities(): Unit = runBlocking {
    // GIVEN
    // (nothing)

    // WHEN
    organizationDatabaseService.deleteAllProviderFrequencyAccountsByOrganizationId(0.toBigInteger())

    // THEN
    // (no errors are thrown)
  }

  @Test
  fun saveProviderFrequencyAccount(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)
    val savedOrganization = organizationRepository.save(organization).awaitSingle()
    val organizationId = savedOrganization.id!!

    val msaId = TestData.Provider.MSA_ID
    val account = ProviderFrequencyAccount.create(msaId, organizationId)

    // WHEN
    organizationDatabaseService.saveProviderFrequencyAccount(account)

    // THEN
    val providerAccounts = providerFrequencyAccountRepository.findAll().collectList().awaitSingle()
    Assertions.assertThat(providerAccounts).hasSize(1)

    val firstProviderAccount = providerAccounts.first()
    Assertions.assertThat(firstProviderAccount.msaId).isEqualTo(msaId)
    Assertions.assertThat(firstProviderAccount.organizationId).isEqualTo(organizationId)
  }

  @Test
  fun deleteAllAssetsByOrganizationId(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)
    val savedOrganization = organizationRepository.save(organization).awaitSingle()
    val organizationId = savedOrganization.id!!

    organizationAssetRepository.save(
      OrganizationAsset.create(
        organizationId,
        OrganizationAssetType.BRAND_LOGO,
        TestData.Asset.BRAND_LOGO_URL,
      )
    )

    // WHEN
    organizationDatabaseService.deleteAllAssetsByOrganizationId(organizationId)

    // THEN
    val assets = organizationAssetRepository.findAll().collectList().awaitSingle()
    Assertions.assertThat(assets).isEmpty()
  }

  @Test
  fun saveOrganizationAsset(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)
    val savedOrganization = organizationRepository.save(organization).awaitSingle()
    val organizationId = savedOrganization.id!!

    val asset = OrganizationAsset.create(
      organizationId,
      OrganizationAssetType.BRAND_LOGO,
      TestData.Asset.BRAND_LOGO_URL,
    )

    // WHEN
    organizationDatabaseService.saveOrganizationAsset(asset)

    // THEN
    val assets = organizationAssetRepository.findAll().collectList().awaitSingle()
    Assertions.assertThat(assets).hasSize(1)

    val firstAsset = assets.first()
    Assertions.assertThat(firstAsset.organizationId).isEqualTo(organizationId)
    Assertions.assertThat(firstAsset.assetType).isEqualTo(OrganizationAssetType.BRAND_LOGO)
    Assertions.assertThat(firstAsset.url).isEqualTo(TestData.Asset.BRAND_LOGO_URL)
  }

  @Test
  fun deleteAllWhitelistedOriginDescriptorsByOrganizationId(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)
    val savedOrganization = organizationRepository.save(organization).awaitSingle()
    val organizationId = savedOrganization.id!!

    whitelistedOriginDescriptorRepository.save(
      WhitelistedOriginDescriptor.create(
        organizationId,
        TestData.Origin.HTTPS.first,
        TestData.Origin.HTTPS.second
      )
    ).awaitSingleOrNull()
    whitelistedOriginDescriptorRepository.save(
      WhitelistedOriginDescriptor.create(
        organizationId,
        TestData.Origin.OTHER_SCHEME.first,
        TestData.Origin.OTHER_SCHEME.second
      )
    ).awaitSingleOrNull()

    // WHEN
    organizationDatabaseService.deleteAllWhitelistedOriginDescriptorsByOrganizationId(organizationId)

    // THEN
    val origins = whitelistedOriginDescriptorRepository.findAll().collectList().awaitSingle()
    Assertions.assertThat(origins).isEmpty()
  }

  @Test
  fun saveWhitelistedOriginDescriptor(): Unit = runBlocking {
    // GIVEN
    val organization = Organization.create(TestData.Organization.DISPLAY_NAME, TestData.Organization.SHORT_CODE)
    val savedOrganization = organizationRepository.save(organization).awaitSingle()
    val organizationId = savedOrganization.id!!

    val origin = WhitelistedOriginDescriptor.create(
      organizationId,
      TestData.Origin.HTTPS.first,
      TestData.Origin.HTTPS.second,
    )

    // WHEN
    organizationDatabaseService.saveWhitelistedOriginDescriptor(origin)

    // THEN
    val origins = whitelistedOriginDescriptorRepository.findAll().collectList().awaitSingle()
    Assertions.assertThat(origins).hasSize(1)

    val firstAsset = origins.first()
    Assertions.assertThat(firstAsset.organizationId).isEqualTo(organizationId)
    Assertions.assertThat(firstAsset.scheme).isEqualTo(TestData.Origin.HTTPS.first)
    Assertions.assertThat(firstAsset.domain).isEqualTo(TestData.Origin.HTTPS.second)
  }

  @Nested
  @DisplayName("Provider Application Tests")
  inner class ProviderApplicationTests {
    
    private lateinit var savedOrganization: Organization
    private lateinit var providerAccount: ProviderFrequencyAccount

    @BeforeEach
    fun setup(): Unit = runBlocking {
      savedOrganization = organizationRepository.save(
        Organization.create(
          TestData.Organization.DISPLAY_NAME,
          TestData.Organization.SHORT_CODE,
        )
      ).awaitSingle()
      providerAccount = providerFrequencyAccountRepository.save(
        ProviderFrequencyAccount.create(
          TestData.Provider.MSA_ID,
          savedOrganization.id!!,
        )
      ).awaitSingle()
      val descriptors = listOf(TestData.Origin.HTTPS, TestData.Origin.OTHER_SCHEME).map { (scheme, domain) ->
        WhitelistedOriginDescriptor.create(
          savedOrganization.id!!,
          scheme,
          domain,
        )
      }
      descriptors.forEach { descriptor ->
        whitelistedOriginDescriptorRepository.save(descriptor).awaitSingle()
      }
      organizationAssetRepository.save(
        OrganizationAsset.create(
          savedOrganization.id!!,
          OrganizationAssetType.BRAND_LOGO,
          TestData.Asset.BRAND_LOGO_URL,
        )
      ).awaitSingle()
    }

    private suspend fun addProviderApplication(): ProviderApplication {
      return providerApplicationRepository.save(
        ProviderApplication.create(
          providerAccount.id!!,
          TestData.ProviderApplication.VERIFIED_CREDENTIAL_URL,
          TestData.ProviderApplication.DISPLAY_NAME,
          TestData.ProviderApplication.SHORT_CODE,
        )
      ).awaitSingle()
    }

    private suspend fun addFullProviderApplication(): ProviderApplication {
      val savedProviderApplication = addProviderApplication()
      val descriptors = listOf(TestData.ApplicationOrigin.HTTPS, TestData.ApplicationOrigin.OTHER_SCHEME).map { (scheme, domain) ->
        ProviderApplicationWhitelistedOriginDescriptor.create(
          savedProviderApplication.id!!,
          scheme,
          domain,
        )
      }
      savedProviderApplication.apply {
        this.providerApplicationWhitelistedOriginDescriptors = descriptors.map { descriptor ->
          providerApplicationWhitelistedOriginDescriptorRepository.save(descriptor).awaitSingle()
        }
      }
      savedProviderApplication.apply {
        this.providerApplicationAssets = listOf(
            providerApplicationAssetRepository.save(
            ProviderApplicationAsset.create(
              savedProviderApplication.id!!,
              OrganizationAssetType.BRAND_LOGO,
              TestData.ApplicationAsset.BRAND_LOGO_URL,
            )
          ).awaitSingle()
        )
      }
      return savedProviderApplication
    }

    @Test
    fun findOneProviderFrequencyAccountByMsaId(): Unit = runBlocking {
      // GIVEN (setup code)
      val providerApplication = addFullProviderApplication()

      // WHEN
      val foundAccount = organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(providerAccount.msaId)

      // THEN
      Assertions.assertThat(foundAccount).isNotNull
      Assertions.assertThat(foundAccount!!.msaId).isEqualTo(providerAccount.msaId)
      Assertions.assertThat(foundAccount.organizationId).isEqualTo(providerAccount.organizationId)
      Assertions.assertThat(foundAccount.providerApplications).hasSize(1)
      Assertions.assertThat(foundAccount.providerApplications[0]).usingRecursiveComparison().isEqualTo(providerApplication)
    }

    @Test
    fun findOneProviderFrequencyAccountByMsaIdNotFound(): Unit = runBlocking {
      // GIVEN (setup code)

      // WHEN
      val foundProviderAccount = organizationDatabaseService.findOneProviderFrequencyAccountByMsaId(BigInteger.TEN)

      // THEN
      Assertions.assertThat(foundProviderAccount).isNull()
    }

    @Test
    fun updateProviderFrequencyAccount(): Unit = runBlocking {
      // GIVEN
      val updatedAccount = providerAccount.copy(msaId = BigInteger.TWO)

      // WHEN
      organizationDatabaseService.updateProviderFrequencyAccount(updatedAccount)

      // THEN
      val foundAccount = providerFrequencyAccountRepository.findById(providerAccount.id!!).awaitSingle()
      Assertions.assertThat(foundAccount).isNotNull
      Assertions.assertThat(foundAccount.msaId).isEqualTo(updatedAccount.msaId)
    }

    @Test
    fun findProviderApplicationByUrl(): Unit = runBlocking {
      // GIVEN
      val savedProviderApplication = addFullProviderApplication()

      // WHEN
      val foundProviderApplication = organizationDatabaseService.findProviderApplicationByUrl(
        TestData.Provider.MSA_ID,
        URI(TestData.ProviderApplication.VERIFIED_CREDENTIAL_URL)
      )

      // THEN
      Assertions.assertThat(foundProviderApplication).isNotNull
      Assertions.assertThat(foundProviderApplication!!.shortCode).isEqualTo(TestData.ProviderApplication.SHORT_CODE)

      Assertions.assertThat(foundProviderApplication.providerApplicationAssets).hasSize(1)
      Assertions.assertThat(foundProviderApplication.providerApplicationAssets.first()).usingRecursiveComparison().comparingOnlyFields("assetType", "url").isEqualTo(
        OrganizationAsset.create(
          savedProviderApplication.id!!,
          OrganizationAssetType.BRAND_LOGO,
          TestData.ApplicationAsset.BRAND_LOGO_URL,
        )
      )

      Assertions.assertThat(foundProviderApplication.providerApplicationWhitelistedOriginDescriptors).hasSize(2)
      listOf(TestData.ApplicationOrigin.HTTPS, TestData.ApplicationOrigin.OTHER_SCHEME).forEach { (scheme, domain) ->
        Assertions.assertThat(foundProviderApplication.providerApplicationWhitelistedOriginDescriptors).anyMatch { it.scheme == scheme && it.domain == domain }
      }
    }

    @Test
    fun findProviderApplicationById(): Unit = runBlocking {
      // GIVEN
      val savedProviderApplication = addFullProviderApplication()

      // WHEN
      val foundProviderApplication = organizationDatabaseService.findProviderApplicationById(savedProviderApplication.id!!)

      // THEN
      Assertions.assertThat(foundProviderApplication).isNotNull
      Assertions.assertThat(foundProviderApplication!!.shortCode).isEqualTo(TestData.ProviderApplication.SHORT_CODE)

      Assertions.assertThat(foundProviderApplication.providerApplicationAssets).hasSize(1)
      Assertions.assertThat(foundProviderApplication.providerApplicationAssets.first()).usingRecursiveComparison().comparingOnlyFields("assetType", "url").isEqualTo(
        OrganizationAsset.create(
          savedProviderApplication.id!!,
          OrganizationAssetType.BRAND_LOGO,
          TestData.ApplicationAsset.BRAND_LOGO_URL,
        )
      )

      Assertions.assertThat(foundProviderApplication.providerApplicationWhitelistedOriginDescriptors).hasSize(2)
      listOf(TestData.ApplicationOrigin.HTTPS, TestData.ApplicationOrigin.OTHER_SCHEME).forEach { (scheme, domain) ->
        Assertions.assertThat(foundProviderApplication.providerApplicationWhitelistedOriginDescriptors).anyMatch { it.scheme == scheme && it.domain == domain }
      }
    }

    @Test
    fun saveProviderApplication(): Unit = runBlocking {
      // WHEN
      val savedProviderApplicationId = organizationDatabaseService.saveProviderApplication(
        addFullProviderApplication()
      )
      val foundProviderApplication = organizationDatabaseService.findProviderApplicationByUrl(
        TestData.Provider.MSA_ID,
        URI(TestData.ProviderApplication.VERIFIED_CREDENTIAL_URL)
      )

      // THEN
      Assertions.assertThat(foundProviderApplication).isNotNull
      Assertions.assertThat(foundProviderApplication!!.shortCode).isEqualTo(TestData.ProviderApplication.SHORT_CODE)

      Assertions.assertThat(foundProviderApplication.providerApplicationAssets).hasSize(1)
      Assertions.assertThat(foundProviderApplication.providerApplicationAssets.first()).usingRecursiveComparison().comparingOnlyFields("assetType", "url").isEqualTo(
        OrganizationAsset.create(
          savedProviderApplicationId,
          OrganizationAssetType.BRAND_LOGO,
          TestData.ApplicationAsset.BRAND_LOGO_URL,
        )
      )

      Assertions.assertThat(foundProviderApplication.providerApplicationWhitelistedOriginDescriptors).hasSize(2)
      listOf(TestData.ApplicationOrigin.HTTPS, TestData.ApplicationOrigin.OTHER_SCHEME).forEach { (scheme, domain) ->
        Assertions.assertThat(foundProviderApplication.providerApplicationWhitelistedOriginDescriptors).anyMatch { it.scheme == scheme && it.domain == domain }
      }
    }

    @Test
    fun deleteAllAssetsByProviderApplicationId(): Unit = runBlocking {
      // GIVEN
      val application = addFullProviderApplication()
      val initialAssets = providerApplicationAssetRepository.findAll().collectList().awaitSingle()
      Assertions.assertThat(initialAssets).isNotEmpty()

      // WHEN
      organizationDatabaseService.deleteAllAssetsByProviderApplicationId(application.id!!)

      // THEN
      val assets = providerApplicationAssetRepository.findAll().collectList().awaitSingle()
      Assertions.assertThat(assets).isEmpty()
    }
    @Test
    fun saveProviderApplicationAsset(): Unit = runBlocking {
      // GIVEN
      val application = addProviderApplication()
      val applicationId = application.id!!

      val asset = ProviderApplicationAsset.create(
        applicationId,
        OrganizationAssetType.BRAND_LOGO,
        TestData.ApplicationAsset.BRAND_LOGO_URL,
      )

      // WHEN
      organizationDatabaseService.saveProviderApplicationAsset(asset)

      // THEN
      val assets = providerApplicationAssetRepository.findAll().collectList().awaitSingle()
      Assertions.assertThat(assets).hasSize(1)

      val firstAsset = assets.first()
      Assertions.assertThat(firstAsset.providerApplicationId).isEqualTo(applicationId)
      Assertions.assertThat(firstAsset.assetType).isEqualTo(OrganizationAssetType.BRAND_LOGO)
      Assertions.assertThat(firstAsset.url).isEqualTo(TestData.ApplicationAsset.BRAND_LOGO_URL)
    }

    @Test
    fun deleteAllWhitelistedOriginDescriptorsByProviderApplicationId(): Unit = runBlocking {
      // GIVEN
      val application = addFullProviderApplication()
      val initialOrigins = providerApplicationWhitelistedOriginDescriptorRepository.findAll().collectList().awaitSingle()
      Assertions.assertThat(initialOrigins).isNotEmpty()

      // WHEN
      organizationDatabaseService.deleteAllWhitelistedOriginDescriptorsByProviderApplicationId(application.id!!)

      // THEN
      val origins = providerApplicationWhitelistedOriginDescriptorRepository.findAll().collectList().awaitSingle()
      Assertions.assertThat(origins).isEmpty()
    }

    @Test
    fun saveProviderApplicationWhitelistedOriginDescriptor(): Unit = runBlocking {
      // GIVEN
      val application = addProviderApplication()
      val applicationId = application.id!!
      val origin = ProviderApplicationWhitelistedOriginDescriptor.create(
        applicationId,
        TestData.ApplicationOrigin.HTTPS.first,
        TestData.ApplicationOrigin.HTTPS.second,
      )

      // WHEN
      organizationDatabaseService.saveProviderApplicationWhitelistedOriginDescriptor(origin)

      // THEN
      val origins = providerApplicationWhitelistedOriginDescriptorRepository.findAll().collectList().awaitSingle()
      Assertions.assertThat(origins).hasSize(1)

      val firstAsset = origins.first()
      Assertions.assertThat(firstAsset.providerApplicationId).isEqualTo(applicationId)
      Assertions.assertThat(firstAsset.scheme).isEqualTo(TestData.ApplicationOrigin.HTTPS.first)
      Assertions.assertThat(firstAsset.domain).isEqualTo(TestData.ApplicationOrigin.HTTPS.second)
    }
  }
}

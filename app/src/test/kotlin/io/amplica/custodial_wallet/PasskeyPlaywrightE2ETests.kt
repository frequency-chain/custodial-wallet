package io.amplica.custodial_wallet

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.microsoft.playwright.*
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.WaitForSelectorState
import com.strategyobject.substrateclient.common.convert.HexConverter
import com.strategyobject.substrateclient.crypto.KeyPair
import com.webauthn4j.util.Base64UrlUtil
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.container.CustodialWalletE2ETestStack
import io.amplica.custodial_wallet.container.FrequencyTestProvider
import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.db.repository.UserDetailType
import io.amplica.custodial_wallet.extension.BrowserEngine
import io.amplica.custodial_wallet.extension.ChromiumBrowserTest
import io.amplica.custodial_wallet.extension.PlaywrightExtension
import io.amplica.custodial_wallet.service.organization.OrganizationData
import io.amplica.custodial_wallet.service.organization.OriginDescriptor
import io.amplica.custodial_wallet.service.organization.ProviderApplicationData
import io.amplica.custodial_wallet.util.*
import io.amplica.custodial_wallet.util.DbUtil.Companion.ALICE_ORGANIZATION
import io.amplica.custodial_wallet.util.DbUtil.Companion.ALICE_PROVIDER_APPLICATION
import io.amplica.custodial_wallet.util.DbUtil.Companion.BOB_ORGANIZATION
import io.amplica.custodial_wallet.util.DbUtil.Companion.BOB_PROVIDER_APPLICATION
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.frequency.client.FrequencyClient
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.provider.CryptoProvider
import io.amplica.frequency.crypto.provider.Secp256K1CryptoProvider
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import java.math.BigInteger
import java.net.URI
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class VirtualAuthenticatorOptions(val options: AuthenticatorOptions)
data class AuthenticatorOptions(val protocol: String, val transport: String, val hasResidentKey: Boolean, val hasUserVerification: Boolean, val isUserVerified: Boolean, val automaticPresenceSimulation: Boolean)
data class RegistrationInfo(val client: CDPSession, val authenticatorIdJSON: JsonObject, val credentialId: String, val seedPhraseListConcatenated: String)

@CustodialWalletE2ESpringTestConfiguration
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ParameterizedClass
@MethodSource("io.amplica.custodial_wallet.parameters.CryptoProviderParameterSource#srAndEthereum")
class PasskeyPlaywrightE2ETests(
  @Qualifier(BeanNames.SES_UTIL) private val sesUtil: SesUtil,
  @Qualifier(BeanNames.API_UTIL) private val apiUtil: ApiUtil,
  @Qualifier(BeanNames.DB_UTIL) private val dbUtil: DbUtil,
) {

  @Parameter
  lateinit var providerCryptoProvider: CryptoProvider

  @Qualifier("objectMapper")
  @Autowired
  private lateinit var objectMapper: ObjectMapper

  @Autowired
  private lateinit var frequencyClient: FrequencyClient

  @Autowired
  lateinit var redisClient: CustodialWalletRedisClient

  @Autowired
  lateinit var databaseService: CustodialWalletDatabaseService

  @LocalServerPort
  private lateinit var serverPort: String

  private val host: String
    get() {
      return "$HOST_NAME:${serverPort}"
    }

  private val callbackUrl: String
    // NOTE: Must be insecure, otherwise browsers throw an SSL error
    get() = "http://$host"

  private val userIdentifierAdminUrl = "https://www.mewe.com"

  private val srProviderOrganization = OrganizationData(
    setOf(srProvider.msaId),
    "SRTest",
    "srpt",
    listOf(OriginDescriptor("https", "charlie.com")),
    emptyMap()
  )

  private val ethProviderOrganization = OrganizationData(
    setOf(ethProvider.msaId),
    "EthTest",
    "etht",
    listOf(OriginDescriptor("https", "delta.com")),
    emptyMap()
  )

  private val srProviderApplicationData = ProviderApplicationData(
    URI("application.charlie.com"),
    "SrProviderApp",
    "srpapp",
    listOf(OriginDescriptor("app", "charlie.com")),
    emptyMap()
  )

  private val ethProviderApplicationData = ProviderApplicationData(
    URI("application.delta.com"),
    "EthProviderApp",
    "ethpapp",
    listOf(OriginDescriptor("app", "delta.com")),
    emptyMap()
  )

  lateinit var userEmail: String
  lateinit var userHandle: String
  lateinit var emailIdentifier: UserIdentifier
  lateinit var userRegistrationData: UserRegistrationData
  lateinit var providerRequest: SiwaRequest

  private lateinit var keyPair: SubstrateOrAccountKeyPair
  private lateinit var provider: FrequencyTestProvider
  private lateinit var providerApplicationData: ProviderApplicationData
  private lateinit var providerOrganizationData: OrganizationData


  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(PasskeyPlaywrightE2ETests::class.java)
    //Change this when wanting to test different type of Passkey Wallets, needs to be symmetrical with the Typescript setup
    //https://github.com/ProjectLibertyLabs/custodial-wallet/blob/7f143152d6d1ff3d79ed205589dce4d0831c1a14/app/src/main/ts/passkey/constants.ts
    private val keyPairType = KeyPairType.SECP256K1

    private val mockNotificationService = MockNotificationService()
    private val mockHCaptchaClient = MockHCaptchaClient()

    @RegisterExtension
    @JvmField
    val pwe = PlaywrightExtension()

    @Container
    val containers = CustodialWalletE2ETestStack()
    @DynamicPropertySource
    @JvmStatic
    fun registerContainerProperties(registry: DynamicPropertyRegistry) {
      containers.registerDynamicProperties(registry)

      registry.add("unfinished.custodial-wallet.notification-service.service_endpoint") {
        "http://localhost:${mockNotificationService.port()}"
      }
      registry.add("unfinished.custodial-wallet.hcaptcha.service_endpoint") {
        "http://localhost:${mockHCaptchaClient.port()}"
      }
      registry.add("unfinished.custodial-wallet.hcaptcha.status_endpoint") {
        "http://localhost:${mockHCaptchaClient.port()}"
      }
    }

    private lateinit var srKeyPair: AccountKeyPair
    private lateinit var ethKeyPair: AccountKeyPair

    private lateinit var srProvider: FrequencyTestProvider
    private lateinit var ethProvider: FrequencyTestProvider

    @JvmStatic
    @BeforeAll
    fun setUpAll() {
      srKeyPair = Sr25519CryptoProvider.createKeyPair()
      ethKeyPair = Secp256K1CryptoProvider.createKeyPair()

      srProvider = containers.frequency.createProvider(
        srKeyPair,
        "SR Provider"
      )
      ethProvider = containers.frequency.createProvider(
        ethKeyPair,
        "ETH Provider"
      )
    }
  }

  @BeforeEach
  fun before() {
    mockNotificationService.reset()

    userEmail = generateUniqueEmail()
    userHandle = generateHandle()
    emailIdentifier = UserIdentifier(userEmail, UserIdentifierType.EMAIL)
    userRegistrationData = UserRegistrationData(emailIdentifier, userHandle, userIdentifierAdminUrl)

    dbUtil.saveOrganizationData(listOf(
      srProviderOrganization to srProviderApplicationData,
      ethProviderOrganization to ethProviderApplicationData,
      ALICE_ORGANIZATION to ALICE_PROVIDER_APPLICATION,
      BOB_ORGANIZATION to BOB_PROVIDER_APPLICATION,
    ))

    when (providerCryptoProvider) {
      Sr25519CryptoProvider -> {
        keyPair = SubstrateOrAccountKeyPair.AccountKeyPairWrapper(srKeyPair)
        provider = srProvider
        providerApplicationData = srProviderApplicationData
        providerOrganizationData = srProviderOrganization
      }

      Secp256K1CryptoProvider -> {
        keyPair = SubstrateOrAccountKeyPair.AccountKeyPairWrapper(ethKeyPair)
        provider = ethProvider
        providerApplicationData = ethProviderApplicationData
        providerOrganizationData = ethProviderOrganization
      }
    }

    providerRequest = createProviderSiwaRequest(
      containers.frequency.wsAddress,
      keyPair,
      DEFAULT_PERMISSIONS,
      callbackUrl,
      userIdentifierAdminUrl,
      SiwaEmailHandling.OTP,
      ApplicationContext(providerApplicationData.verifiedCredentialUrl)
    )
  }

  @AfterEach
  fun tearDown() {
    dbUtil.deleteFromAllTables()
    sesUtil.deleteAllMessages()
  }

  @Nested
  @ParameterizedClass
  @DisplayName("Passkey Wallet Tests")
  @MethodSource("io.amplica.custodial_wallet.parameters.UserKeyPairTypeParameterSource#sr25519Only")
  inner class PasskeyWalletTests {

    @Parameter
    lateinit var userKeyPairType: KeyPairType

    private fun assertSeedPhrasePersistsOnRepeatView(
      page: Page,
      iFrameLocator: FrameLocator,
      seedPhraseDisplayCloseButton: Locator,
      seedPhraseListElements: List<ElementHandle>
    ): String {
      var appendedSeedPhrase = ""
      seedPhraseListElements.forEach { element ->
        appendedSeedPhrase += "${element.innerText()} "
      }

      seedPhraseDisplayCloseButton.click()

      val viewSeedPhraseLink = page.getByTestId("wallet-confirm-view-again")
      assertThat(viewSeedPhraseLink).isVisible()
      viewSeedPhraseLink.click()

      val viewAgainSeedPhraseListElements = iFrameLocator.getByTestId("seedPhraseList").locator("li").elementHandles()
      Assertions.assertEquals(12, viewAgainSeedPhraseListElements.size)

      var viewAgainAppendedSeedPhrase = ""
      viewAgainSeedPhraseListElements.forEach { element ->
        viewAgainAppendedSeedPhrase += "${element.innerText()} "
      }

      Assertions.assertEquals(appendedSeedPhrase, viewAgainAppendedSeedPhrase)

      return appendedSeedPhrase
    }

    private fun getUserAccountId(userDetail: UserDetail): BigInteger = runBlocking {
      databaseService.findUserAccountByUserIdentifier(userDetail)?.id!!
    }

    private fun findUserPasskeyWallet(userAccountId: BigInteger) = runBlocking {
      databaseService.findPasskeyWalletsByUserAccountId(userAccountId)
    }


    private fun loginWithEmail(page: Page) {
      page.navigate(host)

      page.getByTestId("account-button").click()
      page.getByTestId("contact-verification-email-input").fill(userEmail)

      val response = page.waitForResponse("**/api/login/direct") {
        page.getByTestId("contact-verification-submit").click()
      }
      Assertions.assertEquals(response.status(), 200)

      // Retrieve email and navigate to link
      val message = sesUtil.getMessages().last()
      val templateData = EmailTemplateData.fromString(message.TemplateData!!)
      val url = templateData.url.replace("8080", serverPort)
      page.navigate(url)

      // Assert account page loads correctly
      assertThat(page).hasTitle("Frequency Access")
      val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
      assertThat(userHandleFirstLetter).isVisible()
    }

    /**
     * Test end to end entire passkey registration process
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun registerSiwaUserWithPasskey(page: Page): RegistrationInfo {
      //Register user through SIWA to save passkey data to
      siwaRegisterNewUserE2E(
        page,
        sesUtil,
        host,
        apiUtil,
        objectMapper,
        mockNotificationService,
        provider,
        providerRequest,
        isPasskeyEnabled = true,
        userRegistrationData,
        userKeyPairType,
      )
      val validSiwaSession = getValidAuthenticatedSiwaSession(page, redisClient)

      val userAccountId = getUserAccountId(
        UserDetail(
          validSiwaSession.userIdentifier.value,
          UserDetailType.EMAIL,
          1
        )
      )

      //Set up Virtual authenticator options to use
      val authenticatorOptions = AuthenticatorOptions(
        "ctap2", "internal", true, true,
        true, true
      )
      val virtualAuthenticatorOptions = VirtualAuthenticatorOptions(authenticatorOptions)
      val virtualAuthenticatorOptionsJSON: JsonObject = Gson().toJsonTree(virtualAuthenticatorOptions) as JsonObject

      //click "create passkey"
      val createWalletButton = page.getByTestId("create-wallet-submit")
      assertThat(createWalletButton).isVisible()
      createWalletButton.click()

      //Click button to view seed phrase
      val viewSeedPhraseButton = page.getByTestId("wallet-phrase-button")
      assertThat(viewSeedPhraseButton).isVisible()
      viewSeedPhraseButton.click()

      //Verify that iFrame shows up and displays seed phrase
      val iFrameLocator = page.frameLocator("iFrame")
      val seedPhraseList = iFrameLocator.getByTestId("seedPhraseList")
      assertThat(seedPhraseList).isVisible()
      val seedPhraseListElements = seedPhraseList.locator("li").elementHandles()
      Assertions.assertEquals(12, seedPhraseListElements.size)
      val seedPhraseDisplayCloseButton = page.getByTestId("iframe-modal-back")
      assertThat(seedPhraseDisplayCloseButton).isVisible()

      //Store seed phrase and check that when viewing seed phrase again, it is in fact the same seed phrase
      val seedPhraseListString = assertSeedPhrasePersistsOnRepeatView(page, iFrameLocator, seedPhraseDisplayCloseButton, seedPhraseListElements)

      //Close seed phrase display and leave iframe
      assertThat(seedPhraseDisplayCloseButton).isVisible()
      seedPhraseDisplayCloseButton.click()

      //Move to next page to create passkey
      val walletConfirmButton = page.getByTestId("wallet-confirm-button")
      assertThat(walletConfirmButton).isVisible()
      walletConfirmButton.click()

      //Start CDP Session and set up Virtual Authenticator
      val client = page.context().newCDPSession(page)
      client.send("WebAuthn.enable")
      val authenticatorIdJSON: JsonObject =
        client.send("WebAuthn.addVirtualAuthenticator", virtualAuthenticatorOptionsJSON)
      Assertions.assertTrue(authenticatorIdJSON["authenticatorId"] !== null)

      //Create passkey
      val createPasskeyButton = page.getByTestId("passkey-create-button")
      assertThat(createPasskeyButton).isVisible()
      val acceptRegistrationResponse = page.waitForResponse("**/api/passkey/registration/accept") {
        createPasskeyButton.click()
      }

      Assertions.assertEquals(200, acceptRegistrationResponse.status())

      val credentialJson = client.send("WebAuthn.getCredentials", authenticatorIdJSON)
      val credentials = Gson().fromJson(credentialJson, JsonObject::class.java).get("credentials")

      //Verify a credential was in fact created
      Assertions.assertFalse(credentials.asJsonArray.isEmpty)

      val createdCredential = credentials.asJsonArray[0].asJsonObject
      val credentialIdFromVirtualAuthenticator = createdCredential.get("credentialId").toString().replace("\"", "")
      //Note: Virtual Authenticator for some reason encodes the credential Id as Base64 instead of the standard Base64Url
      val virtualAuthenticatorCredentialIdByteArray =
        Base64.decode(credentialIdFromVirtualAuthenticator, 0, credentialIdFromVirtualAuthenticator.length - 1)

      //Verify that a passkey was validated and saved to the custodial wallet
      val userPasskeyWallet = findUserPasskeyWallet(userAccountId)
      val userCredentialIdAsString = userPasskeyWallet[0].credential.credentialIdBase64Url
      val userCredentialIdAsByteArray = Base64UrlUtil.decode(userCredentialIdAsString)
      val userWalletMetadata = runBlocking {
        databaseService.findWalletMetadataByCredentialId(userPasskeyWallet[0].credential.id!!)
      }
      val userCredentialSignatureOfAccountPublicKey = userWalletMetadata!!.credentialSignatureOfAccountPublicKeyBase64Url

      //Verify that the passkey credential saved matches what was created on all ends
      Assertions.assertTrue(userCredentialIdAsByteArray.contentEquals(virtualAuthenticatorCredentialIdByteArray))
      Assertions.assertNotNull(userCredentialSignatureOfAccountPublicKey)

      //After passkey registration is complete and verified behind the scenes, finish passkey creation process and be redirected
      val passkeyCreatedButton = page.getByTestId("passkey-finished-submit")
      assertThat(passkeyCreatedButton).isVisible()
      passkeyCreatedButton.click()

      validateRedirectAndSignedSiwaPayloads(page, validSiwaSession, providerRequest, apiUtil, userHandle, provider.client)

      return RegistrationInfo(client, authenticatorIdJSON, userCredentialIdAsString, seedPhraseListString)
    }

    //For now, use PoC flow just to validate transaction process still works
    private fun fundFromAliceSuccess(page: Page): String {
      val registrationInfo = registerSiwaUserWithPasskey(page)
      val userCredentialId = registrationInfo.credentialId
      val client = registrationInfo.client
      val authenticatorIdJSON = registrationInfo.authenticatorIdJSON

      //Navigate back to home page
      page.navigate("${host}/wallet/passkey-playwright")

      //Navigate to transaction page
      val transactionButton = page.getByTestId("transactionButton")
      val navigateToTransactionPage = page.waitForResponse("**/wallet/passkey-playwright/transaction") {
        transactionButton.click()
      }

      Assertions.assertEquals(200, navigateToTransactionPage.status())

      //Getting sign count prior to authentication to prove that user actually did authenticate properly
      val registeredCredentialResponse: JsonObject = client.send("WebAuthn.getCredentials", authenticatorIdJSON)

      val registeredCredentials =
        Gson().fromJson(registeredCredentialResponse, JsonObject::class.java).get("credentials")
      val preAuthenticationSignCount =
        registeredCredentials.asJsonArray[0].asJsonObject.get("signCount").toString().toInt()

      val fundBtn = page.getByTestId("fundBtn")
      assertThat(fundBtn).isVisible()
      val fundAmount = page.getByTestId("fundAmount")
      fundAmount.fill("100000000")

      val aliceKeyPair =
        "0x98319d4ff8a9508c4bb0cf0b5a78d760a0b2082c02775e6e82370816fedfff48925a225d97aa00682d6a59b95b18780c10d7032336e88f3442b42361f4a66011" +
                "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
      val keyPair = KeyPair.fromBytes(HexConverter.toBytes(aliceKeyPair))

      val publicKey = keyPair.asPublicKey().bytes

      val originalNonce = frequencyClient.getAccountNonce(publicKey).join()

      //Click "fund" button and wait for the "success" div to be visible to show that the balance transfer from Alice to user is complete
      fundBtn.click()
      page.getByTestId("success").waitFor(
        Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(
          30000.0
        )
      )

      val postAuthenticationResponse: JsonObject = client.send("WebAuthn.getCredentials", authenticatorIdJSON)

      val postAuthenticationResponseCredentials =
        Gson().fromJson(postAuthenticationResponse, JsonObject::class.java).get("credentials")
      val postAuthenticationSignCount =
        postAuthenticationResponseCredentials.asJsonArray[0].asJsonObject.get("signCount").toString().toInt()

      Assertions.assertEquals(preAuthenticationSignCount + 1, postAuthenticationSignCount)

      assertOnPageWithElements(page, "Passkey Transaction", "success")
      Assertions.assertTrue(page.getByTestId("success").innerText().startsWith("Funding from alice succeeded"))

      val newNonce = frequencyClient.getAccountNonce(publicKey).join()

      Assertions.assertTrue(newNonce > originalNonce)

      return userCredentialId
    }

    //For now, use PoC flow just to validate transaction process still works
    @ChromiumBrowserTest
    fun passkeyTransactionSuccess(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        //GIVEN a previously authenticated credential
        val userCredentialId = fundFromAliceSuccess(page)

        val unifiedPublicKeyBytes = runBlocking {
          val credentials = databaseService.findPasskeyWalletByCredentialId(userCredentialId)
          val accountPublicKeyBase64Url = credentials!!.wallet.publicKeyBase64Url
          val accountPublicKeyBytes = Base64UrlUtil.decode(accountPublicKeyBase64Url)
          publicKeyToUniversalAddress(accountPublicKeyBytes, keyPairType)
        }

        //WHEN
        //Fill out transfer amount and receiver address to send funds to
        val transferBtn = page.getByTestId("transferBtn")
        assertThat(transferBtn).isVisible()
        val transferAmount = page.getByTestId("transferAmount")
        val receiverAddress = page.getByTestId("receiverAddress")
        transferAmount.fill("100000")
        val alicePublicKeyHex = "0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
        receiverAddress.fill(alicePublicKeyHex)

        //Track nonce changes pre- and post-transaction
        val originalNonce = frequencyClient.getAccountNonce(unifiedPublicKeyBytes).join()

        //Press transaction button and wait for magic to happen
        waitForConsoleMessageStartingWith(page, "Transfer succeeded") {
          transferBtn.click()
        }

        //THEN
        assertOnPageWithElements(page, "Passkey Transaction", "success")
        Assertions.assertTrue(page.getByTestId("success").innerText().startsWith("Transfer of 100000 complete"))

        //New nonce (should be +1 of original)
        val newNonce = frequencyClient.getAccountNonce(unifiedPublicKeyBytes).join()

        Assertions.assertEquals(newNonce, originalNonce + 1)
      }
    }

    @ChromiumBrowserTest
    fun passkeyRegistrationHandleFailure(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        //Create user to save passkey data to
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = true,
          userRegistrationData,
          userKeyPairType,
        )
        val validSiwaSession = getValidAuthenticatedSiwaSession(page, redisClient)

        //Set up Virtual authenticator options to use
        val authenticatorOptions = AuthenticatorOptions(
          "ctap2", "internal", true, true,
          true, true
        )
        val virtualAuthenticatorOptions = VirtualAuthenticatorOptions(authenticatorOptions)
        val virtualAuthenticatorOptionsJSON: JsonObject = Gson().toJsonTree(virtualAuthenticatorOptions) as JsonObject

        //click "create passkey"
        val createWalletButton = page.getByTestId("create-wallet-submit")
        assertThat(createWalletButton).isVisible()
        createWalletButton.click()

        //Click button to view seed phrase
        val viewSeedPhraseButton = page.getByTestId("wallet-phrase-button")
        assertThat(viewSeedPhraseButton).isVisible()
        viewSeedPhraseButton.click()

        //Verify that iFrame shows up and displays seed phrase
        val iFrameLocator = page.frameLocator("iFrame")
        val seedPhraseList = iFrameLocator.getByTestId("seedPhraseList")
        assertThat(seedPhraseList).isVisible()
        val seedPhraseListElements = seedPhraseList.locator("li").elementHandles()
        Assertions.assertEquals(12, seedPhraseListElements.size)
        val seedPhraseDisplayCloseButton = page.getByTestId("iframe-modal-back")
        assertThat(seedPhraseDisplayCloseButton).isVisible()

        //Store seed phrase and check that when viewing seed phrase again, it is in fact the same seed phrase
        assertSeedPhrasePersistsOnRepeatView(page, iFrameLocator, seedPhraseDisplayCloseButton, seedPhraseListElements)

        //Close seed phrase display and leave iframe
        assertThat(seedPhraseDisplayCloseButton).isVisible()
        seedPhraseDisplayCloseButton.click()

        //Move to next page to create passkey
        val walletConfirmButton = page.getByTestId("wallet-confirm-button")
        assertThat(walletConfirmButton).isVisible()
        walletConfirmButton.click()

        //Start CDP Session and set up Virtual Authenticator
        val client = page.context().newCDPSession(page)
        client.send("WebAuthn.enable")
        val authenticatorIdJSON: JsonObject =
          client.send("WebAuthn.addVirtualAuthenticator", virtualAuthenticatorOptionsJSON)
        Assertions.assertTrue(authenticatorIdJSON["authenticatorId"] !== null)

        runBlocking {
          redisClient.deleteSiwaSessionBySessionId(validSiwaSession.id)
        }

        //iFrame sends postMessage back to parent to initiate ajax call to server to validate passkey credential
        val createPasskeyButton = page.getByTestId("passkey-create-button")
        assertThat(createPasskeyButton).isVisible()
        val response = page.waitForResponse("**/api/passkey/registration/accept") {
          createPasskeyButton.click()
        }

        //This will 404 because a session ID "exists" but it's not in redis. Not sure if this is a possible state in reality,
        //But there isn't really a way to "fail" on the passkey credentials being registered and validated
        Assertions.assertEquals(404, response.status())

        //Verify that we are still on the passkey registration page, but that the error text is up
        assertOnPageWithElements(page, "Passkey Wallet", "error-modal", "error-modal-cancel")
      }
    }

    @ChromiumBrowserTest
    fun passkeyRegistrationCancelPasskey(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        //GIVEN
        //Register user through SIWA to save passkey data to
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = true,
          userRegistrationData,
          userKeyPairType,
        )

        //Set up Virtual authenticator options to use
        val authenticatorOptions = AuthenticatorOptions(
          "ctap2", "internal", true, true,
          true, false
        )
        val virtualAuthenticatorOptions = VirtualAuthenticatorOptions(authenticatorOptions)
        val virtualAuthenticatorOptionsJSON: JsonObject = Gson().toJsonTree(virtualAuthenticatorOptions) as JsonObject

        //WHEN
        //click "create passkey"
        val createWalletButton = page.getByTestId("create-wallet-submit")
        assertThat(createWalletButton).isVisible()
        createWalletButton.click()

        //Click button to view seed phrase
        val viewSeedPhraseButton = page.getByTestId("wallet-phrase-button")
        assertThat(viewSeedPhraseButton).isVisible()
        viewSeedPhraseButton.click()

        //Verify that iFrame shows up and displays seed phrase
        val iFrameLocator = page.frameLocator("iFrame")
        val seedPhraseList = iFrameLocator.getByTestId("seedPhraseList")
        assertThat(seedPhraseList).isVisible()

        //Close seed phrase display and leave iframe
        val seedPhraseDisplayCloseButton = page.getByTestId("iframe-modal-back")
        assertThat(seedPhraseDisplayCloseButton).isVisible()
        seedPhraseDisplayCloseButton.click()

        //Move to next page to create passkey
        val walletConfirmButton = page.getByTestId("wallet-confirm-button")
        assertThat(walletConfirmButton).isVisible()
        walletConfirmButton.click()

        //Start CDP Session and set up Virtual Authenticator
        val client = page.context().newCDPSession(page)
        client.send("WebAuthn.enable")
        val authenticatorIdJSON: JsonObject =
          client.send("WebAuthn.addVirtualAuthenticator", virtualAuthenticatorOptionsJSON)
        Assertions.assertTrue(authenticatorIdJSON["authenticatorId"] !== null)

        //create passkey
        val createPasskeyButton = page.getByTestId("passkey-create-button")
        assertThat(createPasskeyButton).isVisible()
        createPasskeyButton.click()

        //THEN
        //Verify that a credential was NOT created
        val credentialCount = client.send("WebAuthn.getCredentials", authenticatorIdJSON)
        Assertions.assertTrue(credentialCount.getAsJsonArray("credentials").isEmpty)

        //Verify that we are still on the passkey registration page and not an error page
        assertOnPageWithElements(page, "Passkey Wallet", "passkey-create-setup-title")
      }
    }

    @ChromiumBrowserTest
    fun passkeyRegistrationNoSessionId(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->

        val response = page.navigate("${host}/wallet/passkey-playwright")

        Assertions.assertEquals(400, response.status())
      }
    }

    @ChromiumBrowserTest
    fun passkeyTransactionHandleFailure(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        //GIVEN: Necessary info from registered credential to test authentication
        registerSiwaUserWithPasskey(page)

        //WHEN
        //Navigate back to home page
        page.navigate("${host}/wallet/passkey-playwright")

        //Navigate to transaction page
        val transactionButton = page.getByTestId("transactionButton")
        val navigateToTransactionPage = page.waitForResponse("**/wallet/passkey-playwright/transaction") {
          transactionButton.click()
        }

        //THEN
        Assertions.assertEquals(200, navigateToTransactionPage.status())

        val transferBtn = page.getByTestId("transferBtn")
        assertThat(transferBtn).isVisible()
        val transferAmount = page.getByTestId("transferAmount")
        val receiverAddress = page.getByTestId("receiverAddress")
        transferAmount.fill("1000000")
        receiverAddress.fill("ufh48034qhpfj")
        transferBtn.click()

        assertOnPageWithElements(page, "Passkey Transaction", "error")
      }
    }

    @ChromiumBrowserTest
    fun passkeyWalletCreationFailsForUserWithExistingWallet(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        registerSiwaUserWithPasskey(page)

        //Set up Virtual authenticator options to use
        val authenticatorOptions = AuthenticatorOptions(
          "ctap2", "internal", true, true,
          true, true
        )
        val virtualAuthenticatorOptions = VirtualAuthenticatorOptions(authenticatorOptions)
        val virtualAuthenticatorOptionsJSON: JsonObject = Gson().toJsonTree(virtualAuthenticatorOptions) as JsonObject

        //Navigate to passkey registration page
        page.navigate("${host}/wallet/passkey")

        //Click button to view seed phrase
        val viewSeedPhraseButton = page.getByTestId("wallet-phrase-button")
        assertThat(viewSeedPhraseButton).isVisible()
        viewSeedPhraseButton.click()

        //Verify that iFrame shows up and displays seed phrase
        val iFrameLocator = page.frameLocator("iFrame")
        val seedPhraseList = iFrameLocator.getByTestId("seedPhraseList")
        assertThat(seedPhraseList).isVisible()
        val seedPhraseListElements = seedPhraseList.locator("li").elementHandles()
        Assertions.assertEquals(12, seedPhraseListElements.size)
        val seedPhraseDisplayCloseButton = page.getByTestId("iframe-modal-back")
        assertThat(seedPhraseDisplayCloseButton).isVisible()

        //Store seed phrase and check that when viewing seed phrase again, it is in fact the same seed phrase
        assertSeedPhrasePersistsOnRepeatView(page, iFrameLocator, seedPhraseDisplayCloseButton, seedPhraseListElements)

        //Close seed phrase display and leave iframe
        assertThat(seedPhraseDisplayCloseButton).isVisible()
        seedPhraseDisplayCloseButton.click()


        //Move to next page to create passkey
        val walletConfirmButton = page.getByTestId("wallet-confirm-button")
        assertThat(walletConfirmButton).isVisible()
        walletConfirmButton.click()

        //Open iFrame to create passkey
        val createPasskeyButton = page.getByTestId("passkey-create-button")
        assertThat(createPasskeyButton).isVisible()

        //Start CDP Session and set up Virtual Authenticator
        val client = page.context().newCDPSession(page)
        client.send("WebAuthn.enable")
        val authenticatorIdJSON: JsonObject =
          client.send("WebAuthn.addVirtualAuthenticator", virtualAuthenticatorOptionsJSON)
        Assertions.assertTrue(authenticatorIdJSON["authenticatorId"] !== null)

        //WHEN
        //iFrame sends postMessage back to parent to initiate ajax call to server to validate passkey credential
        val acceptRegistrationResponse = page.waitForResponse("**/api/passkey/registration/accept") {
          createPasskeyButton.click()
        }

        //THEN
        Assertions.assertEquals(409, acceptRegistrationResponse.status())

        //Verify that we are still on the passkey registration page
        assertOnPageWithElements(page, "Passkey Wallet", "passkey-create-setup-title")

        // Assert that error modal is showing
        assertThat(page.getByTestId("error-modal-title")).containsText("Error")
        assertThat(page.getByTestId("error-modal-desc")).containsText("An error has occurred")
      }
    }

    @ChromiumBrowserTest
    fun passkeyWalletRemindMeLater(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        //GIVEN
        //Register user through SIWA to save passkey data to
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = true,
          userRegistrationData,
          userKeyPairType,
        )

        //Get authenticated siwa session
        val validSiwaSession = getValidAuthenticatedSiwaSession(page, redisClient)

        //WHEN
        //click remind later button
        val skipWalletButton = page.getByTestId("skip-wallet")
        assertThat(skipWalletButton).isVisible()
        skipWalletButton.click()

        //THEN
        validateRedirectAndSignedSiwaPayloads(page, validSiwaSession, providerRequest, apiUtil, userHandle, provider.client)
      }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @ChromiumBrowserTest
    fun registerPasskeyWalletFromAccountPage(engine: BrowserEngine) {
      // GIVEN
      // Create an account via SIWA
      pwe.createContext(engine).use { (_, page) ->
        // Register user through SIWA to save passkey data to
        siwaRegisterNewUserE2E(
          page,
          sesUtil,
          host,
          apiUtil,
          objectMapper,
          mockNotificationService,
          provider,
          providerRequest,
          isPasskeyEnabled = true,
          userRegistrationData,
          userKeyPairType,
        )

        // Get authenticated siwa session
        val validSiwaSession = getValidAuthenticatedSiwaSession(page, redisClient)

        // Click remind later button
        val skipWalletButton = page.getByTestId("skip-wallet")
        assertThat(skipWalletButton).isVisible()
        skipWalletButton.click()

        validateRedirectAndSignedSiwaPayloads(page, validSiwaSession, providerRequest, apiUtil, userHandle, provider.client)
      }

      pwe.createContext(engine).use { (_, page) ->
        //Login to Frequency Access with created email
        loginWithEmail(page)

        // WHEN
        // Initiate passkey wallet flow
        page.navigate("$host/wallet/passkey")

        //Set up Virtual authenticator options to use
        val authenticatorOptions = AuthenticatorOptions(
          "ctap2", "internal", true, true,
          true, true
        )
        val virtualAuthenticatorOptions = VirtualAuthenticatorOptions(authenticatorOptions)
        val virtualAuthenticatorOptionsJSON: JsonObject = Gson().toJsonTree(virtualAuthenticatorOptions) as JsonObject

        //Click button to view seed phrase
        page.getByTestId("wallet-phrase-button").click()

        //Verify that iFrame shows up and displays seed phrase
        val iFrameLocator = page.frameLocator("iFrame")
        val seedPhraseList = iFrameLocator.getByTestId("seedPhraseList")
        assertThat(seedPhraseList).isVisible()

        //Close seed phrase display and leave iframe
        page.getByTestId("iframe-modal-back").click()

        //Move to next page to create passkey
        val walletConfirmButton = page.getByTestId("wallet-confirm-button")
        assertThat(walletConfirmButton).isVisible()
        walletConfirmButton.click()

        //Start CDP Session and set up Virtual Authenticator
        val client = page.context().newCDPSession(page)
        client.send("WebAuthn.enable")
        val authenticatorIdJSON: JsonObject =
          client.send("WebAuthn.addVirtualAuthenticator", virtualAuthenticatorOptionsJSON)
        Assertions.assertTrue(authenticatorIdJSON["authenticatorId"] !== null)

        //Create passkey
        val createPasskeyButton = page.getByTestId("passkey-create-button")
        assertThat(createPasskeyButton).isVisible()
        val acceptRegistrationResponse = page.waitForResponse("**/api/passkey/registration/accept") {
          createPasskeyButton.click()
        }

        // THEN
        Assertions.assertEquals(200, acceptRegistrationResponse.status())

        val credentialJson = client.send("WebAuthn.getCredentials", authenticatorIdJSON)
        val credentials = Gson().fromJson(credentialJson, JsonObject::class.java).get("credentials")

        //Verify a credential was in fact created
        Assertions.assertFalse(credentials.asJsonArray.isEmpty)

        val createdCredential = credentials.asJsonArray[0].asJsonObject
        val credentialIdFromVirtualAuthenticator = createdCredential.get("credentialId").toString().replace("\"", "")
        //Note: Virtual Authenticator for some reason encodes the credential Id as Base64 instead of the standard Base64Url
        val virtualAuthenticatorCredentialIdByteArray =
          Base64.decode(credentialIdFromVirtualAuthenticator, 0, credentialIdFromVirtualAuthenticator.length - 1)

        //Verify that a passkey was validated and saved to the custodial wallet
        val userAccountId = getUserAccountId(UserDetail(userEmail, UserDetailType.EMAIL))
        val userPasskeyWallet = findUserPasskeyWallet(userAccountId)
        val userCredentialIdAsString = userPasskeyWallet[0].credential.credentialIdBase64Url
        val userCredentialIdAsByteArray = Base64UrlUtil.decode(userCredentialIdAsString)
        val userWalletMetadata = runBlocking {
          databaseService.findWalletMetadataByCredentialId(userPasskeyWallet[0].credential.id!!)
        }
        val userCredentialSignatureOfAccountPublicKey = userWalletMetadata!!.credentialSignatureOfAccountPublicKeyBase64Url

        // Verify that the passkey credential saved matches what was created on all ends
        Assertions.assertTrue(userCredentialIdAsByteArray.contentEquals(virtualAuthenticatorCredentialIdByteArray))
        Assertions.assertNotNull(userCredentialSignatureOfAccountPublicKey)

        //After passkey registration is complete and verified behind the scenes, finish passkey creation process and be redirected
        val passkeyCreatedButton = page.getByTestId("passkey-finished-submit")
        assertThat(passkeyCreatedButton).isVisible()
        passkeyCreatedButton.click()
      }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @ChromiumBrowserTest
    fun recoverPasskeyWallet(engine: BrowserEngine) {
      pwe.createContext(engine).use { (_, page) ->
        val registrationInfo = registerSiwaUserWithPasskey(page)
        val seedPhraseList = registrationInfo.seedPhraseListConcatenated
        val preExistingUserCredentialIdBase64Url = registrationInfo.credentialId
        val client = registrationInfo.client
        val authenticatorIdJSON = registrationInfo.authenticatorIdJSON

        //Login to Frequency Access
        loginWithEmail(page)

        //Navigate to recovery page
        val navigateToRecoveryPageButton = page.getByTestId("account-passkey-wallet-recovery-submit")
        val navigateToRecoveryPageResponse = page.waitForResponse("**/wallet/recovery?") {
          navigateToRecoveryPageButton.click()
        }

        Assertions.assertEquals(200, navigateToRecoveryPageResponse.status())

        //Verify that iFrame shows up as textarea
        val iFrameLocator = page.frameLocator("iframe")

        //Find seed phrase input
        val walletRecoveryInput = iFrameLocator.getByTestId("walletRecoveryInput")

        //Enter seed phrase
        walletRecoveryInput.fill(seedPhraseList)
        Assertions.assertEquals(seedPhraseList, walletRecoveryInput.inputValue())

        //Click submit
        val submitRecoverySeedButton = page.getByTestId("wallet-recovery-button")
        assertThat(submitRecoverySeedButton).isVisible()
        submitRecoverySeedButton.click()


        //Finish normal passkey registration
        val createPasskeyButton = page.getByTestId("passkey-create-button")
        assertThat(createPasskeyButton).isVisible()
        val acceptRegistrationResponse = page.waitForResponse("**/api/passkey/registration/accept") {
          createPasskeyButton.click()
        }

        // THEN
        Assertions.assertEquals(200, acceptRegistrationResponse.status())

        val credentialJson = client.send("WebAuthn.getCredentials", authenticatorIdJSON)
        val credentials = Gson().fromJson(credentialJson, JsonObject::class.java).get("credentials")

        //Verify a credential was in fact created
        Assertions.assertFalse(credentials.asJsonArray.isEmpty)

        //Since this is using the pre-existing virtual authenticator, this will have a list of more than one credential
        //This list of credentials comes back randomized and not in order that they're created
        //It's easier to just create a list of the credential id as byteArrays and check off this list
        val createdCredentialsAsByteArrayList = credentials.asJsonArray.map {
          val createdCredential = it.asJsonObject.get("credentialId").toString().replace("\"", "")
          Base64.decode(createdCredential, 0, createdCredential.length - 1)
        }

        //Verify that a passkey was validated and saved to the custodial wallet
        val userAccountId = getUserAccountId(UserDetail(userEmail, UserDetailType.EMAIL))
        val userPasskeyWallet = findUserPasskeyWallet(userAccountId)

        //Grabbing newly created user credential id
        val newlyCreatedUserCredentialIdAsString = userPasskeyWallet[1].credential.credentialIdBase64Url
        val newlyCreatedUserCredentialIdAsByteArray = Base64UrlUtil.decode(newlyCreatedUserCredentialIdAsString)
        val userWalletMetadataList = runBlocking {
          databaseService.findWalletMetadataByWalletId(userPasskeyWallet[0].wallet.id!!)
        }

        val originalUserCredentialSignatureOfAccountPublicKey = userWalletMetadataList[0].credentialSignatureOfAccountPublicKeyBase64Url
        val newlyCreatedUserCredentialSignatureOfAccountPublicKey = userWalletMetadataList[1].credentialSignatureOfAccountPublicKeyBase64Url

        // Verify that the passkey credential saved matches what was created on all ends
        Assertions.assertTrue(createdCredentialsAsByteArrayList.any {
          it.contentEquals(newlyCreatedUserCredentialIdAsByteArray)
        })
        Assertions.assertNotNull(newlyCreatedUserCredentialSignatureOfAccountPublicKey)

        // Verify that new passkey credential created is not some duplicate of the originally created passkey credential
        Assertions.assertNotEquals(newlyCreatedUserCredentialIdAsString, preExistingUserCredentialIdBase64Url)
        Assertions.assertTrue(userPasskeyWallet.any {
          it.credential.credentialIdBase64Url == preExistingUserCredentialIdBase64Url
        })
        Assertions.assertNotEquals(originalUserCredentialSignatureOfAccountPublicKey, newlyCreatedUserCredentialSignatureOfAccountPublicKey)

        //After passkey registration is complete and verified behind the scenes, finish passkey creation process and be redirected
        val passkeyCreatedButton = page.getByTestId("passkey-finished-submit")
        assertThat(passkeyCreatedButton).isVisible()
        passkeyCreatedButton.click()
      }
    }
  }
}

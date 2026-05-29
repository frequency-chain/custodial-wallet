package io.amplica.custodial_wallet

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Response
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.container.CustodialWalletE2ETestStack
import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.db.repository.UserDetailType
import io.amplica.custodial_wallet.dto.SiwaPayloadResponse
import io.amplica.custodial_wallet.extension.BrowserEngine
import io.amplica.custodial_wallet.extension.BrowserTest
import io.amplica.custodial_wallet.extension.ContextPagePair
import io.amplica.custodial_wallet.extension.PlaywrightExtension
import io.amplica.custodial_wallet.orchestration.siwa.SiwaOrchestrationService
import io.amplica.custodial_wallet.service.password.PasswordService
import io.amplica.custodial_wallet.siggen.SignatureGenerator
import io.amplica.custodial_wallet.util.*
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import io.amplica.custodial_wallet.web.SESSION_ID_COOKIE_NAME
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.RegisterExtension
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
import java.time.Instant


@CustodialWalletE2ESpringTestConfiguration
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  // NOTE(Julian, 2024-08-20): This property has been updated to be correct in the test config, but
  // these tests depend on the original wrong value, so it has been preserved until the tests can be updated.
  properties = ["unfinished.custodial-wallet.frequency.graph.schema.ids={11,12,13,14}"]
)
class PlaywrightE2ETests(
  @Qualifier(BeanNames.SES_UTIL) private val sesUtil: SesUtil,
  @Qualifier(BeanNames.API_UTIL) private val apiUtil: ApiUtil,
  @Qualifier(BeanNames.DB_UTIL) private val dbUtil: DbUtil,
) {

  @Autowired
  lateinit var siwaOrchestrationService: SiwaOrchestrationService

  @Autowired
  lateinit var redisClient: CustodialWalletRedisClient

  @Autowired
  lateinit var databaseService: CustodialWalletDatabaseService

  @Autowired
  lateinit var passwordService: PasswordService

  @LocalServerPort
  private lateinit var serverPort: String

  private val host: String
    get() {
      return "${HOST_NAME}:${serverPort}"
    }

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(PlaywrightE2ETests::class.java)

    // NOTE(Julian, 2024-08-20): These values are not correct. 7-10 are the graph schemas and there is no ID greater than 11.
    val DEFAULT_SCHEMA_IDS = listOf(5, 7, 8, 9, 10)
    private const val USER_IDENTIFIER_ADMIN_URL = "https://www.mewe.com"
    private val mockNotificationService = MockNotificationService()

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
    }
  }

  @BeforeEach
  fun before() {
    dbUtil.saveOrganizationData()
  }

  @AfterEach
  fun tearDown() {
    dbUtil.deleteFromAllTables()
    sesUtil.deleteAllMessages()
  }

  fun userBatchPayloadToSignRequest(
    userIdentifier: UserIdentifier,
    providerMsaId: BigInteger,
    providerPublicKeyDto: PublicKeyDto,
    callback: String,
  ): BatchPayloadToSignRequest {
    val addProviderPayload = AddProviderPayloadRequest(
      providerMsaId,
      DEFAULT_SCHEMA_IDS,
      URI("https://www.mewe.com/authenticate")
    )
    val addProviderSignature = SignatureGenerator.generateAddProviderRequestSignature(addProviderPayload)
    val addProviderType = PayloadType.ADD_PROVIDER
    val typedAddProviderPayload = TypedPayloadRequestWithSignature(addProviderSignature, addProviderType, addProviderPayload)

    val claimHandlePayload = HandlePayloadRequest(generateHandle())
    val claimHandleSignature = SignatureGenerator.generateHandleRequestSignature(claimHandlePayload)
    val claimHandleType = PayloadType.CLAIM_HANDLE
    val typedClaimHandlePayload = TypedPayloadRequestWithSignature(claimHandleSignature, claimHandleType, claimHandlePayload)

    return BatchPayloadToSignRequest(
      null,
      userIdentifier.value,
      UserIdentifier(userIdentifier.value, userIdentifier.type),
      providerPublicKeyDto,
      callback,
      listOf(typedAddProviderPayload, typedClaimHandlePayload),
      null,
      null,
    )
  }

  private suspend fun siwaRegisterUser(userRegistrationData: UserRegistrationData): SiwaPayloadResponse {
    val siwaPayloadResponse = siwaRegisterNewUserOrchestration(
      siwaOrchestrationService,
      containers.frequency,
      redisClient, userRegistrationData
    )
    return siwaPayloadResponse
  }

  private fun loginWithEmail(engine: BrowserEngine, userEmail: String): ContextPagePair {
    // Start browser context and page without a `use`
    val contextPagePair = pwe.createContext(engine)
    val (_, page) = contextPagePair

    page.navigate(host)

    findButtonAndClick(page, "account-button")
    findInputAndFill(page, "contact-verification-email-input", userEmail)

    val response = findButtonAndClickWithResponse(page, "contact-verification-submit", "**/api/login/direct")
    Assertions.assertEquals(response.status(), 200)

    // Retrieve email and navigate to link
    val message = sesUtil.getMessages().last()
    val templateData = EmailTemplateData.fromString(message.TemplateData!!)
    val url = templateData.url.replace("8080", serverPort)
    page.navigate(url)
    assertThat(page).hasTitle("Frequency Access")
    val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
    assertThat(userHandleFirstLetter).isVisible()

    return contextPagePair
  }

  fun getUserAccountId(userDetail: UserDetail): BigInteger = runBlocking {
    databaseService.findUserAccountByUserIdentifier(userDetail)?.id!!
  }

  fun savePassword(userAccountId: BigInteger, password: String) = runBlocking {
    passwordService.savePasswordByUserAccountId(userAccountId, password)
  }

  fun authenticatePassword(userAccountId: BigInteger, password: String): Boolean = runBlocking {
    passwordService.authenticateByUserAccountId(userAccountId, password)
  }

  fun failToFindElement(page: Page, elementTestId: String): Locator {
    val element = page.getByTestId(elementTestId)
    val isVisible = element.isVisible
    Assertions.assertFalse(isVisible)
    return element
  }

  fun findButtonAndClick(page: Page, buttonTestId: String) {
    val button = page.getByTestId(buttonTestId)
    button.click()
  }

  fun findButtonAndClick(locator: Locator, buttonTestId: String) {
    val button = locator.getByTestId(buttonTestId)
    button.click()
  }

  fun findButtonAndClickWithResponse(page: Page, buttonTestId: String, responseUrl: String): Response {
    return page.waitForResponse(responseUrl, Page.WaitForResponseOptions().setTimeout(10000.0)) {
      findButtonAndClick(page, buttonTestId)
    }
  }

  fun findButtonAndClickWithResponse(page: Page, locator: Locator, buttonTestId: String, responseUrl: String): Response {
    return page.waitForResponse(responseUrl, Page.WaitForResponseOptions().setTimeout(10000.0)) {
      findButtonAndClick(locator, buttonTestId)
    }
  }

  fun findInputAndFill(page: Page, inputTestId: String, fillValue: String) {
    val input = page.getByTestId(inputTestId)
    input.fill(fillValue)
    assertThat(input).hasValue(fillValue)
  }

  fun findInputAndFill(locator: Locator, inputTestId: String, fillValue: String) {
    val input = locator.getByTestId(inputTestId)
    input.fill(fillValue)
    assertThat(input).hasValue(fillValue)
  }

  fun getSessionIdFromCookie(page: Page): String {
    val sessionIdCookie = page.context().cookies().find { cookie ->
      cookie.name.equals(SESSION_ID_COOKIE_NAME)
    } ?: throw Exception("No Session Id cookie found")
    return sessionIdCookie.value
  }

  private fun createEmailBatchPayloadToSignRequest(userEmail: String): BatchPayloadToSignRequest {
    return userBatchPayloadToSignRequest(
      UserIdentifier(userEmail, UserIdentifierType.EMAIL),
      containers.frequency.aliceProviderMsaId,
      containers.frequency.aliceProviderPublicKeyDto,
      "http://localhost:8080",
    )
  }

  private fun createSmsBatchPayloadToSignRequest(userPhoneNumber: String): BatchPayloadToSignRequest {
    return userBatchPayloadToSignRequest(
      UserIdentifier(userPhoneNumber, UserIdentifierType.PHONE_NUMBER),
      containers.frequency.aliceProviderMsaId,
      containers.frequency.aliceProviderPublicKeyDto,
      "http://localhost:8080",
    )
  }


  /** Asserts that the `verifiedDate` field has been set for the `UserIdentifier` in the database */
  private fun assertVerifiedDateWasJustSet(userDetail: UserDetail) {
    val userIdentifier = runBlocking { databaseService.findUserIdentifier(userDetail) }

    // Assert timestamp is from the last 10 seconds
    val tenSecondsAgo = Instant.now().minusSeconds(10).toEpochMilli().toBigInteger()
    Assertions.assertTrue(tenSecondsAgo <= userIdentifier?.verifiedDate!!)
    Assertions.assertTrue(userIdentifier.verifiedDate!! <= Instant.now().toEpochMilli().toBigInteger())
  }

  /**
   * Asserts that the `verifiedDate` field was set for the `UserIdentifier` in the database, and it is more recent
   * than the entity's creation.
   */
  private fun assertVerifiedDateWasJustUpdated(userDetail: UserDetail) {
    assertVerifiedDateWasJustSet(userDetail)

    val userIdentifier = runBlocking { databaseService.findUserIdentifier(userDetail) }
    // Assert the `verifiedDate` was updated *after* signup
    Assertions.assertTrue(userIdentifier?.createdAt!! < userIdentifier.verifiedDate!!)
  }


  private fun generateEmailUserRegistrationData(): UserRegistrationData {
    val userIdentifier =  generateUserIdentifier()
    return UserRegistrationData(userIdentifier, generateHandle(), USER_IDENTIFIER_ADMIN_URL)
  }

  private fun generateSmsUserRegistrationData(phoneNumber: String? = null): UserRegistrationData {
    val userPhoneNumber = phoneNumber ?: generateUniquePhone(false)
    val userIdentifier =  UserIdentifier(userPhoneNumber, UserIdentifierType.PHONE_NUMBER)
    return UserRegistrationData(userIdentifier, generateHandle(), USER_IDENTIFIER_ADMIN_URL)
  }

  @Nested
  @DisplayName("Website Email Login Tests")
  inner class WebsiteEmailLoginTests {
    @BrowserTest
    fun emailLoginSucceeds(engine: BrowserEngine): Unit = runBlocking {

      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

      pwe.createContext(engine).use { (_, page) ->

        page.navigate(host)

        findButtonAndClick(page, "account-button")
        findInputAndFill(page, "contact-verification-email-input", userEmail)

        val response = findButtonAndClickWithResponse(page, "contact-verification-submit", "**/api/login/direct")
        Assertions.assertEquals(response.status(), 200)

        // Retrieve email and navigate to link
        val message = sesUtil.getMessages().last()
        val templateData = EmailTemplateData.fromString(message.TemplateData!!)
        val url = templateData.url.replace("8080", serverPort)
        page.navigate(url)
        assertThat(page).hasTitle("Frequency Access")
        val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
        assertThat(userHandleFirstLetter).isVisible()

        assertVerifiedDateWasJustUpdated(UserDetail(userEmail, UserDetailType.EMAIL))
      }
    }

    @BrowserTest
    fun revokeDelegationSucceeds(engine: BrowserEngine): Unit = runBlocking {
      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

      pwe.createContext(engine).use { (_, page) ->
        page.navigate("$host/")

        findButtonAndClick(page,"account-button")
        findInputAndFill(page, "contact-verification-email-input", userEmail)
        findButtonAndClickWithResponse(page, "contact-verification-submit", "**/api/login/direct")

        // Retrieve email and navigate to link
        val message = sesUtil.getMessages().last()
        val templateData = EmailTemplateData.fromString(message.TemplateData!!)
        val url = templateData.url.replace("8080", serverPort)
        page.navigate(url)
        assertThat(page).hasTitle("Frequency Access")
        val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
        assertThat(userHandleFirstLetter).isVisible()

        findButtonAndClick(page, "view-provider-details")

        val revokeButton = page.getByTestId("revoke-button")
        assertThat(revokeButton).isVisible()
        revokeButton.click()

        val confirmRevoke = page.getByTestId("confirm-revoke")
        assertThat(confirmRevoke).isVisible()
        confirmRevoke.click()

        assertThat(revokeButton).isHidden()
        assertThat(confirmRevoke).isHidden()

        // User clicks 'Log out'
        page.getByTestId("page.account").getByTestId("account-button").click()

        //Login again
        findButtonAndClick(page,"account-button")
        findInputAndFill(page, "contact-verification-email-input", userEmail)
        findButtonAndClickWithResponse(page, "contact-verification-submit", "**/api/login/direct")

        // Retrieve email and navigate to link
        val newMessage = sesUtil.getMessages().last()
        val newTemplateData = EmailTemplateData.fromString(newMessage.TemplateData!!)
        val newUrl = newTemplateData.url.replace("8080", serverPort)
        page.navigate(newUrl)
        assertThat(page).hasTitle("Frequency Access")
        val newUserHandleFirstLetter = page.getByTestId("user-handle-first-letter")
        assertThat(newUserHandleFirstLetter).isVisible()

        val newRevokeButton = page.getByTestId("revoke-button")
        val newConfirmRevoke = page.getByTestId("confirm-revoke")
        assertThat(newConfirmRevoke).isHidden()
        assertThat(newRevokeButton).isHidden()
      }
    }

    private fun checkAndLoginWithEmail(page: Page, userEmail: String) {
      val message = sesUtil.getMessages().last()
      val templateData = EmailTemplateData.fromString(message.TemplateData!!)
      val url = templateData.url.replace("8080", serverPort)
      page.navigate(url)
      assertThat(page).hasTitle("Frequency Access")
      val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
      assertThat(userHandleFirstLetter).isVisible()

      //Just in case to make sure we're on the account we're looking for
      val isEmailPresent = page.locator("text=$userEmail").count() == 1
      Assertions.assertTrue(isEmailPresent)
    }

    @BrowserTest
    fun addContactEmailSucceeds(engine: BrowserEngine): Unit = runBlocking {
      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

      //This will be used as a new email
      val addEmail = generateUniqueEmail()

      pwe.createContext(engine).use { (_, page) ->

        val errors = collectJavaScriptErrors(engine, page)

        page.navigate("$host/")

        findButtonAndClick(page, "account-button")
        findInputAndFill(page, "contact-verification-email-input", userEmail)
        findButtonAndClickWithResponse(page, "contact-verification-submit", "**/api/login/direct")

        // Retrieve email and navigate to link
        checkAndLoginWithEmail(page, userEmail)

        //Find Add Contact link
        findButtonAndClick(page, "add-contact-button")
        val addContactModal = page.getByTestId("add-contact-modal")
        Assertions.assertTrue(addContactModal.isVisible)

        findInputAndFill(addContactModal, "contact-verification-email-input", addEmail)
        val verifyEmailResponse = findButtonAndClickWithResponse(page, addContactModal, "contact-verification-submit", "**/contact/email/verify")

        Assertions.assertEquals(verifyEmailResponse.status(), 202)
        // Check that there are no JS errors on the account page
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty()

        // Retrieve email and navigate to link
        checkAndLoginWithEmail(page, addEmail)
      }
    }

    @BrowserTest
    fun addContactEmailInvalidEmailCases(engine: BrowserEngine): Unit = runBlocking {
      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

      pwe.createContext(engine).use { (_, page) ->
        val errors = collectJavaScriptErrors(engine, page)
        page.navigate("$host/")

        findButtonAndClick(page, "account-button")
        findInputAndFill(page, "contact-verification-email-input", userEmail)
        findButtonAndClickWithResponse(page, "contact-verification-submit", "**/api/login/direct")

        // Retrieve email and navigate to link
        checkAndLoginWithEmail(page, userEmail)

        // Check that there are no JS errors on the account page
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty()

        //Find Add Contact link
        findButtonAndClick(page, "add-contact-button")
        val addContactModal = page.getByTestId("add-contact-modal")
        Assertions.assertTrue(addContactModal.isVisible)

        findInputAndFill(addContactModal, "contact-verification-email-input", userEmail)
        findButtonAndClick(addContactModal, "contact-verification-submit")

        assertThat(addContactModal.getByTestId("title")).containsText("User Already Exists")
        findButtonAndClick(page, "close-button")

        findInputAndFill(addContactModal, "contact-verification-email-input", "fjeofea9psuefijaespoifas")
        findButtonAndClick(addContactModal, "contact-verification-submit")
        assertThat(addContactModal.getByTestId("title")).containsText("Invalid Email")
      }
    }
  }

  @Nested
  @DisplayName("Website Sms Login Tests")
  inner class WebsiteSmsLoginTests {

    private fun getToken(page: Page): String = runBlocking {
      val sessionId = getSessionIdFromCookie(page)
      val websiteSession = redisClient.findWebsiteSessionBySessionId(sessionId)
      websiteSession!!.authenticationCode!!
    }

    private fun getVerificationToken(page: Page): String = runBlocking {
      val sessionId = getSessionIdFromCookie(page)
      val websiteSession = redisClient.findWebsiteSessionBySessionId(sessionId)
      websiteSession!!.verificationCode!!
    }

    /** Note: Returns an 'open' context & page that must be closed by the caller (e.g., `use` or `close()`) */
    private fun loginWithSms(
      engine: BrowserEngine,
      phoneNumber: String,
      smsCodeModalCallback: (page: Page, smsCode: String) -> Boolean
    ): ContextPagePair {
      // Start browser context and page without a `use`
      val contextPagePair = pwe.createContext(engine)
      val (_, page) = contextPagePair

      page.navigate(host)

      findButtonAndClick(page, "account-button")
      findButtonAndClick(page, "switch-button")
      page.getByTestId("phone-form").fill(phoneNumber)

      val response = findButtonAndClickWithResponse(page, "contact-verification-submit", "**/api/login/direct")
      Assertions.assertEquals(200, response.status())

      // Get Code
      val smsCode = getToken(page)

      val continueLogin = smsCodeModalCallback(page, smsCode)

      if(continueLogin) {
        // Confirm we are on the My Account page
        val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
        assertThat(userHandleFirstLetter).isVisible()
      }

      return contextPagePair
    }

    private fun loginWithSmsWithCodeFillCallback(
      engine: BrowserEngine,
      phoneNumber: String
    ): ContextPagePair = loginWithSms(engine, phoneNumber) { page, smsCode ->
      page.getByTestId("contact-verification-input-sms-code").fill(smsCode)
      findButtonAndClick(page, "sms-code-submit")
      true
    }

    @BrowserTest
    fun smsLoginSucceeds(engine: BrowserEngine): Unit = runBlocking {
      val userRegistrationData = generateSmsUserRegistrationData()
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val phoneNumber = userRegistrationData.userIdentifier.value

      loginWithSmsWithCodeFillCallback(engine, phoneNumber).close()
      assertVerifiedDateWasJustUpdated(UserDetail(phoneNumber, UserDetailType.PHONE_NUMBER))
    }

    @BrowserTest
    fun smsLoginThenLogoutSucceeds(engine: BrowserEngine): Unit = runBlocking {

      val userRegistrationData = generateSmsUserRegistrationData()
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val phoneNumber = userRegistrationData.userIdentifier.value

      loginWithSmsWithCodeFillCallback(engine, phoneNumber).use { (_, page) ->
        // User clicks 'Log out'
        page.getByTestId("page.account").getByTestId("account-button").click()

        // Browser redirects to the homepage
        assertThat(page.getByTestId("page.index")).isVisible()

        // User is no longer able to access their account and is shown the error page
        page.navigate("${host}/account")
        assertThat(page.getByText("Whitelabel Error Page")).isVisible()
      }
    }

    /**
     * Incorrect token triggers the appropriate 'wrong token' modal, and then allows user to log in with correct token
     */
    @BrowserTest
    fun smsLoginFatFingerRecoversGracefully(engine: BrowserEngine): Unit = runBlocking {

      val userRegistrationData = generateSmsUserRegistrationData()
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val phoneNumber = userRegistrationData.userIdentifier.value

      loginWithSms(engine, phoneNumber) { page, smsCode ->
        val incorrectCode = "000000"

        // Wait for code entry modal to load to then type code and submit
        page.getByTestId("contact-verification-input-sms-code").fill(incorrectCode)
        findButtonAndClick(page, "sms-code-submit")

        // Expect modal to explain the error correctly
        assertThat(page.getByText("Invalid Token")).isVisible()

        // Expect sms modal to be on error page
        val smsModal = page.getByTestId("contact-verification-modal-error")
        assertThat(smsModal).isVisible()

        // Close the error modal
        smsModal.getByTestId("close-button").click()
        page.getByTestId("contact-verification-input-sms-code").fill(smsCode)

        findButtonAndClick(page, "sms-code-submit")
        true
      }.close()
    }

    @BrowserTest
    @Tag("Slow")
    fun smsLoginEntersIncorrectCodeOverRetryLimitFails(engine: BrowserEngine): Unit = runBlocking {
      val userRegistrationData = generateSmsUserRegistrationData()
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val phoneNumber = userRegistrationData.userIdentifier.value

      loginWithSms(engine, phoneNumber) { page, _ ->
        val incorrectCode = "000000"

        repeat(5) {
          // Wait for code entry modal to load to then type code and submit
          page.getByTestId("contact-verification-input-sms-code").fill(incorrectCode)
          findButtonAndClick(page, "sms-code-submit")

          // Expect modal to explain the error correctly
          assertThat(page.getByText("Invalid Token")).isVisible()

          // Expect sms modal to be on error page
          val smsModal = page.getByTestId("contact-verification-modal-error")
          assertThat(smsModal).isVisible()

          smsModal.getByTestId("close-button").click()
        }

        // Wait for code entry modal to load to then type code and submit
        page.getByTestId("contact-verification-input-sms-code").fill(incorrectCode)

        val response = page.waitForResponse("**/api/login/authenticate") {
          findButtonAndClick(page, "sms-code-submit")
        }

        // Expect to get a different status as a result of triggering `ApiError.INCORRECT_TOKEN_RETRY_LIMIT_EXCEEDED`
        Assertions.assertEquals(403, response.status())

        // The error modal copy should be generic
        val errorModal = page.getByTestId("component.error-modal-web")
        assertThat(errorModal.getByTestId("title")).hasText("Error")
        assertThat(errorModal.getByTestId("message")).hasText("An error has occurred")
        false
      }.close()
    }

    @BrowserTest
    fun addContactSmsSucceeds(engine: BrowserEngine): Unit = runBlocking {
      val userRegistrationData = generateSmsUserRegistrationData()
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val phoneNumber = userRegistrationData.userIdentifier.value
      val addContactPhoneNumber = generateUniquePhone(false)

      loginWithSmsWithCodeFillCallback(engine, phoneNumber).use { (_, page) ->
        val errors = collectJavaScriptErrors(engine, page)

        //Find Add Contact link
        findButtonAndClick(page, "add-contact-button")
        val addContactModal = page.getByTestId("add-contact-modal")
        Assertions.assertTrue(addContactModal.isVisible)

        //Switch to phone number form
        findButtonAndClick(addContactModal, "switch-button")
        page.getByTestId("phone-form").fill(addContactPhoneNumber)
        val verifySmsResponse = findButtonAndClickWithResponse(page, "contact-verification-submit", "**/contact/sms/verify")

        Assertions.assertEquals(verifySmsResponse.status(), 202)

        val smsCode = getVerificationToken(page)
        page.getByTestId("contact-verification-input-sms-code").fill(smsCode)
        findButtonAndClick(page, "sms-code-submit")

        //Check if new number is in the contact section
        val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
        assertThat(userHandleFirstLetter).isVisible()
        val isNewPhoneNumberPresent = page.locator("text=$addContactPhoneNumber").count() == 1
        Assertions.assertTrue(isNewPhoneNumberPresent)

        // Check that there are no JS errors on the account page
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty()
      }
    }

    @BrowserTest
    fun addContactSmsInvalidCases(engine: BrowserEngine): Unit = runBlocking {

      val userRegistrationData = generateSmsUserRegistrationData()
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val phoneNumber = userRegistrationData.userIdentifier.value

      loginWithSmsWithCodeFillCallback(engine, phoneNumber).use { (_, page) ->
        val errors = collectJavaScriptErrors(engine, page)

        // Check that there are no JS errors on the account page
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty()

        //Find Add Contact link
        findButtonAndClick(page, "add-contact-button")
        val addContactModal = page.getByTestId("add-contact-modal")
        Assertions.assertTrue(addContactModal.isVisible)

        //first try to enter nothing
        findButtonAndClick(addContactModal, "switch-button")
        findButtonAndClick(page, "contact-verification-submit")
        assertThat(addContactModal.getByTestId("title")).containsText("Internal Error")
        findButtonAndClick(page, "close-button")

        //Then enter an invalid phone phoneNumber
        findButtonAndClick(addContactModal, "switch-button")
        page.getByTestId("phone-form").fill("123")
        findButtonAndClick(page, "contact-verification-submit")
        assertThat(addContactModal.getByTestId("title")).containsText("Invalid Phone Number")
        findButtonAndClick(page, "close-button")

        //Then try to add an existing phone phoneNumber
        findButtonAndClick(addContactModal, "switch-button")
        page.getByTestId("phone-form").fill(phoneNumber)
        findButtonAndClick(page, "contact-verification-submit")
        assertThat(addContactModal.getByTestId("title")).containsText("User Already Exists")
        findButtonAndClick(page, "close-button")
      }
    }
  }

  // Does not include Login, Password Login tests will have
  // their own section like other login methods
  @Nested
  @DisplayName("Password Tests")
  inner class PasswordTests {
    @BrowserTest
    fun changePasswordSucceeds(engine: BrowserEngine): Unit = runBlocking {
      // Create Account with Email
      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

      // Create Password for account
      val userDetail = UserDetail(userEmail, UserDetailType.EMAIL)
      val userAccountId = getUserAccountId(userDetail)
      val password = generateUniquePassword()
      savePassword(userAccountId, password)
      Assertions.assertTrue(authenticatePassword(userAccountId, password))

      // Login to FA with email
      loginWithEmail(engine, userEmail).use { (_, page) ->
        val errors = collectJavaScriptErrors(engine, page)

        findButtonAndClick(page, "view-provider-details")
        findButtonAndClick(page, "change-password-button")

        // On account page, fill in new password fields
        val newPassword = generateUniquePassword()
        findInputAndFill(page, "change-password-input", newPassword)
        findInputAndFill(page, "change-password-retype", newPassword)

        // Submit new password
        val response = findButtonAndClickWithResponse(page, "confirm-change-password-button", "**/api/account/password")

        // check successful password change
        Assertions.assertEquals(200, response.status())
        assertElementsVisible(page, "change-password-changed")
        Assertions.assertTrue(authenticatePassword(userAccountId, newPassword))

        // Check that there are no JS errors on the account page
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty()
      }
    }

    @BrowserTest
    fun changePasswordWithoutExistingPasswordNoInputBox(engine: BrowserEngine): Unit = runBlocking {
      // Create Account with Email
      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)


      // Login to FA with email
      loginWithEmail(engine, userEmail).use { (_, page) ->
        findButtonAndClick(page, "view-provider-details")

        failToFindElement(page, "change-password-input")
        failToFindElement(page, "change-password-retype")
        failToFindElement(page, "change-password-button")
      }
    }
  }

  @Nested
  @DisplayName("Website Password Tests")
  @Disabled("Disabling Until Svelte Website Supports Password Login (Ticket #1589)")
  inner class WebsitePasswordTests {
    @BrowserTest
    fun passwordLoginSucceedsWithEmailUsername(engine: BrowserEngine): Unit = runBlocking {
      // Create Account with Email
      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

      val password = generateUniquePassword()
      val userAccountId = getUserAccountId(UserDetail(userEmail, UserDetailType.EMAIL))
      savePassword(userAccountId, password)
      pwe.createContext(engine).use { (_, page) ->
        page.navigate(host)

        findButtonAndClick(page, "account-button")
        findButtonAndClick(page, "email-switch-button-password")
        findInputAndFill(page, "username-input", userEmail)
        findInputAndFill(page, "password-input", password)

        val response = findButtonAndClickWithResponse(page, "password-submit", "**/api/login/password")
        Assertions.assertEquals(response.status(), 200)

        assertThat(page).hasTitle("Frequency Access")
        val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
        assertThat(userHandleFirstLetter).isVisible()
      }
    }

    @BrowserTest
    fun passwordLoginSucceedsWithSmsUsername(engine: BrowserEngine): Unit = runBlocking {
      val userRegistrationData = generateSmsUserRegistrationData()
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val phoneNumber = userRegistrationData.userIdentifier.value

      val password = generateUniquePassword()
      val userAccountId = getUserAccountId(UserDetail(phoneNumber, UserDetailType.PHONE_NUMBER))
      savePassword(userAccountId, password)
      pwe.createContext(engine).use { (_, page) ->

        page.navigate(host)

        findButtonAndClick(page, "account-button")
        findButtonAndClick(page, "email-switch-button-password")
        findInputAndFill(page, "username-input", phoneNumber)
        findInputAndFill(page, "password-input", password)

        val response = findButtonAndClickWithResponse(page, "password-submit", "**/api/login/password")
        Assertions.assertEquals(response.status(), 200)

        assertThat(page).hasTitle("Frequency Access")
        val userHandleFirstLetter = page.getByTestId("user-handle-first-letter")
        assertThat(userHandleFirstLetter).isVisible()
      }
    }
  }

  @Nested
  @DisplayName("OAuth Batch Signing Tests")
  @Tag("Shelved")
  inner class OAuthBatchSigningTests {
    @BrowserTest
    fun batchSigningSucceedsEmail(engine: BrowserEngine): Unit = runBlocking {
      // Create Account with Email
      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

      pwe.createContext(engine).use { (_, page) ->

        // Submit the batch payload to be signed
        val batchPayloadToSignRequest = createEmailBatchPayloadToSignRequest(userEmail)
        val batchPayloadResponse = apiUtil.postBatchPayloadRequest(batchPayloadToSignRequest)
        Assertions.assertEquals(batchPayloadResponse.statusCode.value(), 201)

        // get the location and call the permission page
        val permissionLocation = batchPayloadResponse.headers.location ?: throw Exception("No Location found")
        page.navigate("${host}${permissionLocation.path}")

        // check the page and sessionId cookie
        getSessionIdFromCookie(page)
        assertOnPageWithElements(page, "Signing Permission", "batchPermissions-instructions", "addProvider-msaId", "addProvider-url")

        val permissionSubmitResponse = findButtonAndClickWithResponse(page, "batchPermissions-submit", "**/web/sign/accept")
        Assertions.assertEquals(permissionSubmitResponse.status(), 200)

        // check the page to see that we are on the "email sent" page
        assertOnPageWithElements(page, "Amplica Authentication Code Email Sent", "auth-code-email-sent")
      }
    }

    @BrowserTest
    fun batchSigningSucceedsSms(engine: BrowserEngine): Unit = runBlocking {
      val userRegistrationData = generateSmsUserRegistrationData()
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)
      val phoneNumber = userRegistrationData.userIdentifier.value

      pwe.createContext(engine).use { (_, page) ->

        // Submit the batch payload to be signed
        val batchPayloadToSignRequest = createSmsBatchPayloadToSignRequest(phoneNumber)
        val batchPayloadResponse = apiUtil.postBatchPayloadRequest(batchPayloadToSignRequest)
        Assertions.assertEquals(batchPayloadResponse.statusCode.value(), 201)

        // get the location and call the permission page
        val permissionLocation = batchPayloadResponse.headers.location ?: throw Exception("No Location found")
        page.navigate("${host}${permissionLocation.path}")

        // check the page and sessionId cookie
        getSessionIdFromCookie(page)
        assertOnPageWithElements(page, "Signing Permission", "batchPermissions-instructions", "addProvider-msaId", "addProvider-url")

        val permissionSubmitResponse = findButtonAndClickWithResponse(page, "batchPermissions-submit", "**/web/sign/accept")
        Assertions.assertEquals(permissionSubmitResponse.status(), 200)

        // check the page to see that we are on the "sms sent, enter auth code" page
        assertOnPageWithElements(page, "Amplica Authentication Code SMS Sent", "auth-sms-code", "auth-sms-code-input", "auth-sms-code-submit-button")
      }
    }
  }

  @Nested
  @DisplayName("Account Tests")
  inner class AccountTests {
    @BrowserTest
    fun changeHandleSucceeds(engine: BrowserEngine): Unit = runBlocking {
      val newHandle = "newHandle"

      // Create Account with Email
      val userRegistrationData = generateEmailUserRegistrationData()
      val userEmail = userRegistrationData.userIdentifier.value
      val siwaPayloadResponse = siwaRegisterUser(userRegistrationData)
      submitSiwaSignedPayloads(containers.frequency.aliceProviderClient, siwaPayloadResponse)

      // Login to FA with email
      loginWithEmail(engine, userEmail).use { (_, page) ->
        val errors = collectJavaScriptErrors(engine, page)

        // On account page, open and fill change handle modal
        findButtonAndClick(page, "change-handle-button")
        findInputAndFill(page, "enter-handle-input", newHandle)

        // Submit request
        val response = findButtonAndClickWithResponse(page, "change-handle-submit-button", "**/api/account/handle")

        // check successful password change
        Assertions.assertEquals(200, response.status())
        assertElementsVisible(page, "change-handle-success")

        // Check that there are no JS errors on the account page
        org.assertj.core.api.Assertions.assertThat(errors).isEmpty()
      }
    }
  }
}

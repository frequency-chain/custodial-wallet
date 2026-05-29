package io.amplica.custodial_wallet.orchestration.passkey

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.data.*
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.attestation.statement.*
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.validator.exception.ValidationException
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.controller.PasskeyWalletController.Companion.ACCOUNT_PAGE_PATH
import io.amplica.custodial_wallet.db.data.PasskeyWallet
import io.amplica.custodial_wallet.db.repository.Credential
import io.amplica.custodial_wallet.db.repository.Wallet
import io.amplica.custodial_wallet.db.repository.WalletMetadata
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.util.EthereumHelper
import io.amplica.custodial_wallet.util.base64UrlEncode
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.util.switchEncoding
import io.amplica.custodial_wallet.web.AUTHORIZATION_CODE_PARAMETER_NAME
import io.amplica.custodial_wallet.web.LoggingAttributes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.web.util.UriComponentsBuilder
import java.math.BigInteger
import java.util.*


class DefaultPasskeyWalletService(
  private var webAuthnManager: WebAuthnManager,
  private var origin: String,
  private var rpId: String,
  private val custodialWalletDbService: CustodialWalletDatabaseService,
  private val signingOrchestrationService: SigningOrchestrationService,
  private val lookupOrchestrationService: LookupOrchestrationService,
  private val environment: Environment,
  override val passkeyWalletPageIsEnabled: Boolean,
) : PasskeyWalletService {

  private val LOG: Logger = LoggerFactory.getLogger(DefaultPasskeyWalletService::class.java)

  override suspend fun acceptRegistrationRequest(
    sessionId: String,
    acceptRegistrationRequest: AcceptRegistrationRequest
  ): Boolean {
    //Get UserAccountId for this Session
    val userAccountId = getUserAccountIdOrThrow(sessionId)

    //Assert that this is either a first time registration or recovery
    val accountPublicKeyHex = acceptRegistrationRequest.accountPublicKey.encodedValue
    if(!isPasskeyWalletFirstTimeRegistrationOrRecovery(userAccountId, accountPublicKeyHex)) {
      throw ApiException(
        ApiError.PASSKEY_WALLET_ALREADY_EXISTS_FOR_THIS_USER_ACCOUNT,
        "User $userAccountId attempted to register a second passkey wallet account key pair",
        mapOf(LoggingAttributes.SESSION_ID to sessionId)
      )
    }

    //Parse Registration Request
    val registrationData = parseRegistrationRequest(acceptRegistrationRequest)

    val authenticatorData = registrationData.attestationObject?.authenticatorData ?: throw ApiException(
      ApiError.UNABLE_TO_PARSE_REGISTRATION_REQUEST, "Unable to parse registration request"
    )
    val attestedCredentialData = authenticatorData.attestedCredentialData ?: throw ApiException(
      ApiError.UNABLE_TO_PARSE_REGISTRATION_REQUEST, "Unable to parse registration request"
    )

    //Validate Registration Request
    validateRegistrationRequest(acceptRegistrationRequest, registrationData)

    //Verify Credential Public Key Signature
    verifyCredentialPublicKeySignature(acceptRegistrationRequest, eip712Enabled = true)

    //Save PasskeyWallet to Database
    savePasskeyWalletToDatabase(acceptRegistrationRequest, attestedCredentialData, authenticatorData, userAccountId)
    return true
  }

  override suspend fun retrieveCredentialAccount(sessionId: String, credentialId: String): CredentialResponseDto {
    //Essentially works as a check to see if there's an active SIWA session, but also can use this to see if a user has other passkeys on different devices
    val userAccountId = getUserAccountIdOrThrow(sessionId)
    val userPasskeyWallet: PasskeyWallet = getCredential(userAccountId, credentialId, sessionId)
    return consolidateCredentialResponseDto(userPasskeyWallet)
  }

  override suspend fun retrieveCredentials(sessionId: String): CredentialResponsesDto {
    val userAccountId = getUserAccountIdOrThrow(sessionId)
    val userPasskeyWallets = custodialWalletDbService.findPasskeyWalletsByUserAccountId(userAccountId)
    val credentialResponses = mutableListOf<CredentialResponseDto>()
    userPasskeyWallets.forEach{ userPasskeyWallet ->
      val credentialResponseDto = consolidateCredentialResponseDto(userPasskeyWallet)
      credentialResponses.add(
        credentialResponseDto
      )
    }
    return CredentialResponsesDto(credentialResponses)
  }

  override suspend fun getCallbackUrl(sessionId: String): String {
    val siwaSession = lookupOrchestrationService.findSiwaSession(sessionId)
    return when (siwaSession) {
      is AuthenticatedSiwaSession -> {
        // When the user has an authenticated SIWA session the callback URL from the request is used to redirect the
        // user back to the provider
        UriComponentsBuilder.fromUriString(siwaSession.fullCallbackUrl)
          .queryParam(AUTHORIZATION_CODE_PARAMETER_NAME, siwaSession.authorizationCode)
          .encode().build().toUriString()
      }

      is UnauthenticatedSiwaSession -> {
        throw ApiException(
          ApiError.SIWA_SESSION_UNAUTHENTICATED,
          "An authenticated session is required to view this page",
          mapOf(LoggingAttributes.SESSION_ID to sessionId)
        )
      }

      else -> {
        // Assert the user has a logged in website session
        val websiteSession = lookupOrchestrationService.findWebsiteSessionBySessionId(sessionId)
        if (websiteSession.loggedIn != UserState.LOGGED_IN) {
          throw ApiException(
            ApiError.NO_LOGGED_IN_SESSION_FOUND,
            "An authenticated session is required to view this page",
            mapOf(LoggingAttributes.SESSION_ID to sessionId)
          )
        }

        ACCOUNT_PAGE_PATH
      }
    }
  }

  /***
   * Grabs the account public key encoded in hex for a user
   */
  override suspend fun getAccountPublicKeyHexOrThrow(sessionId: String): String {
    val userAccountId = when (val siwaSession = lookupOrchestrationService.findSiwaSession(sessionId)) {
      is AuthenticatedSiwaSession -> {
        siwaSession.userAccountId
      }
      is UnauthenticatedSiwaSession -> {
        throw ApiException(
          ApiError.SIWA_SESSION_UNAUTHENTICATED,
          "An authenticated session is required to recover an account",
          mapOf(LoggingAttributes.SESSION_ID to sessionId)
        )
      }
      else -> {
        val websiteSession = lookupOrchestrationService.findWebsiteSessionBySessionId(sessionId)
        if (websiteSession.loggedIn != UserState.LOGGED_IN) {
          throw ApiException(
            ApiError.NO_LOGGED_IN_SESSION_FOUND,
            "A logged in website session is required to recover an account",
            mapOf(LoggingAttributes.SESSION_ID to sessionId)
          )
        }
        val userAccountIdList = websiteSession.userAccountIds
        if (userAccountIdList.isNullOrEmpty()) {
          throw ApiException(
            ApiError.NO_USER_ACCOUNT_ID_FOUND,
            "No User Account Id was found in this website session",
            mapOf(LoggingAttributes.SESSION_ID to sessionId)
          )
        } else if(userAccountIdList.size > 1) {
          throw ApiException(
            ApiError.MULTIPLE_USER_ACCOUNT_IDS_IN_WEBSITE_SESSION_FOUND,
            "Multiple user account ids were found in this website session",
            mapOf(LoggingAttributes.SESSION_ID to sessionId)
          )
        } else{
          userAccountIdList.first()
        }
      }
    }

    //Note: In theory all passkey wallet account public keys should be the same across all wallets
    //so only grabbing one is safe
    if(userAccountId != null) {
      val passkeyWalletList: List<PasskeyWallet> = custodialWalletDbService.findPasskeyWalletsByUserAccountId(userAccountId)
      if (passkeyWalletList.isNotEmpty()) {
        val accountPublicKeyBase64UrlSet = passkeyWalletList.map { passkeyWallet ->
          passkeyWallet.wallet.publicKeyBase64Url
        }.toSet()

        //If set contains only one account public key, return that
        //If there is an empty set, throw an error since there should be no existing wallets without a public key
        //Otherwise throw an error regarding non-unique account public keys
        if(accountPublicKeyBase64UrlSet.size == 1) {
          return switchEncoding(accountPublicKeyBase64UrlSet.first(), Encoding.BASE64_URLENCODED, Encoding.HEX)
        } else if(accountPublicKeyBase64UrlSet.isEmpty()) {
          throw ApiException(
            ApiError.PASSKEY_WALLET_EXISTS_WITHOUT_ACCOUNT_PUBLIC_KEY,
            "Wallet exists for user $userAccountId without account public key",
            mapOf(LoggingAttributes.SESSION_ID to sessionId)
            )
        } else {
          throw ApiException(
            ApiError.PASSKEY_WALLET_WITH_NON_UNIQUE_ACCOUNT_PUBLIC_KEY,
            "One or more non-unique account public keys saved under user $userAccountId",
            mapOf(LoggingAttributes.SESSION_ID to sessionId)
          )
        }
      } else {
        //If there are no existing wallets yet, throw an error
        throw ApiException(
          ApiError.PASSKEY_WALLET_NOT_FOUND_FOR_THIS_USER,
          "This user $userAccountId does not have a passkey wallet registered",
          mapOf(LoggingAttributes.SESSION_ID to sessionId)
          )
      }
    } else {
      throw ApiException(
        ApiError.NO_USER_ACCOUNT_ID_FOUND,
        "No User Account Id was found in this session",
        mapOf(LoggingAttributes.SESSION_ID to sessionId)
      )
    }
  }

  override suspend fun walletExistsForAccount(userAccountId: BigInteger): Boolean {
    return custodialWalletDbService.findPasskeyWalletsByUserAccountId(userAccountId).isNotEmpty()
  }

  private suspend fun isPasskeyWalletFirstTimeRegistrationOrRecovery(userAccountId: BigInteger, accountPublicKeyHex: String): Boolean {
    val userPasskeyWalletList = custodialWalletDbService.findPasskeyWalletsByUserAccountId(userAccountId)
    val accountPublicKeyList = if(userPasskeyWalletList.isNotEmpty()) {
      //If a user has a passkey wallet, check to make sure that the passkey wallet(s) do not have a different account public key
      userPasskeyWalletList.filter {
        it.wallet.publicKeyBase64Url == switchEncoding(accountPublicKeyHex, Encoding.HEX, Encoding.BASE64_URLENCODED)
      }
    } else {
      //If empty, this is a first time registration as a user does not have a passkey wallet
      return true
    }
    return accountPublicKeyList.size == 1
  }

  private fun parseRegistrationRequest(acceptRegistrationRequest: AcceptRegistrationRequest): RegistrationData {
    val registrationRequest = RegistrationRequest(
      acceptRegistrationRequest.attestationObject.toByteArray(),
      acceptRegistrationRequest.clientDataJSON.toByteArray(),
      acceptRegistrationRequest.clientExtensions,
      acceptRegistrationRequest.transports
    )
    return try {
      registrationRequest.attestationObject
      webAuthnManager.parse(registrationRequest)
    } catch (e: DataConversionException) {
      throw ApiException(
        ApiError.UNABLE_TO_PARSE_REGISTRATION_REQUEST, "Unable to parse registration request", e
      )
    }
  }

  private fun validateRegistrationRequest(
    acceptRegistrationRequest: AcceptRegistrationRequest,
    registrationData: RegistrationData
  ) {
    val activeProfile = environment.activeProfiles[0]
    val port = environment.getProperty("local.server.port")
    if (activeProfile == "test") {
      origin = "http://localhost:$port"
    }
    val serverProperty = ServerProperty(
      Origin(origin),
      rpId,
      {
        acceptRegistrationRequest.challenge.toByteArray()
      },
      null
    )
    val pubKeyCredParams =
      listOf(PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256))
    val registrationParameters = RegistrationParameters(serverProperty, pubKeyCredParams, false, true)
    try {
      val credentialSignatureOfAccountPublicKey = acceptRegistrationRequest.credentialSignatureOfAccountPublicKey
      if(credentialSignatureOfAccountPublicKey != null) {
        val attestationObject = registrationData.attestationObject
        if(attestationObject != null) {
          val signatureInBytes = credentialSignatureOfAccountPublicKey.toSignatureBytes()
          fun validateSignatureOrThrow(challengeSignature: ByteArray) {
            if(!signatureInBytes.contentEquals(challengeSignature)) {
              throw ApiException(ApiError.UNABLE_TO_VALIDATE_REGISTRATION_REQUEST, "Unable to validate registration the webauthn signature and credentialSignatureOfAccountPublicKey do not match")
            }
          }
          when (val attestationStatement = attestationObject.attestationStatement) {
            //PMF As of 20250103 these are the only AttestationStatements that support retrieving the signature in bytes as provided via webauthn
            is FIDOU2FAttestationStatement -> validateSignatureOrThrow(attestationStatement.sig)
            is PackedAttestationStatement -> validateSignatureOrThrow(attestationStatement.sig)
            is AndroidKeyAttestationStatement -> validateSignatureOrThrow(attestationStatement.sig)
            is TPMAttestationStatement -> validateSignatureOrThrow(attestationStatement.sig)
            else -> {
              LOG.warn("AttestationStatement of class '${attestationStatement.javaClass.name}' is not supported yet'")
            }
          }
        }
      } else{
        LOG.warn("Unable to validate registration request, credentialSignatureOfAccountPublicKey is null")
      }
      webAuthnManager.validate(registrationData, registrationParameters)
    } catch (e: ValidationException) {
      throw ApiException(
        ApiError.UNABLE_TO_VALIDATE_REGISTRATION_REQUEST, "Unable to validate registration request webauthn validation failed", e
      )
    }
  }

  /**
   * Asserts that the provider signature over the message containing the credential public key was performed
   * by the account key pair.
   */
  private fun verifyCredentialPublicKeySignature(
    acceptRegistrationRequest: AcceptRegistrationRequest,
    eip712Enabled: Boolean,
  ) {
    // Unpack request object
    val accountPublicKey = acceptRegistrationRequest.accountPublicKey
    val passkeyCompressedPublicKey = acceptRegistrationRequest.passkeyCompressedPublicKey
    val credentialPublicKeySignature = acceptRegistrationRequest.credentialPublicKeySignature

    // Decode the request data to byte arrays
    val signatureBytes = credentialPublicKeySignature.toSignatureBytes()
    val passkeyCompressedPublicKeyBytes = passkeyCompressedPublicKey.toPublicKeyBytes()
    val accountPublicKeyBytes = accountPublicKey.toPublicKeyBytes()

    val verified = if (eip712Enabled) {
      signingOrchestrationService.verifySignedPublicKey(
        accountPublicKey,
        passkeyCompressedPublicKeyBytes,
        credentialPublicKeySignature
      )
    } else {
      when (credentialPublicKeySignature.algo) {
        SignatureKeyPairType.SECP256K1 -> EthereumHelper.verifySignature(
          accountPublicKeyBytes,
          passkeyCompressedPublicKeyBytes,
          signatureBytes,
        )

        else -> {
          signingOrchestrationService.verifySignedPublicKey(
            accountPublicKey,
            passkeyCompressedPublicKeyBytes,
            credentialPublicKeySignature
          )
        }
      }
    }

    if (!verified) {
      throw ApiException(
        ApiError.INVALID_SIGNATURE, "Account Key Signature is invalid"
      )
    }
  }

  private suspend fun savePasskeyWalletToDatabase(
    acceptRegistrationRequest: AcceptRegistrationRequest,
    attestedCredentialData: AttestedCredentialData,
    authenticatorData: AuthenticatorData<RegistrationExtensionAuthenticatorOutput>,
    userAccountId: BigInteger
  ) {
    val passkeyCompressedPublicKeyBase64Url = base64UrlEncode(
      acceptRegistrationRequest.passkeyCompressedPublicKey.toPublicKeyBytes()
    )
    val credentialPublicKeySignatureBase64Url =
      base64UrlEncode(
        acceptRegistrationRequest.credentialPublicKeySignature.toSignatureBytes()
      )
    val credentialSignatureOfAccountPublicKeyBase64Url =
      if (acceptRegistrationRequest.credentialSignatureOfAccountPublicKey != null) {
        base64UrlEncode(
          acceptRegistrationRequest.credentialSignatureOfAccountPublicKey!!.toSignatureBytes()
        )
      } else null

    val credential = Credential.create(
      UUID.nameUUIDFromBytes(
        attestedCredentialData.aaguid.bytes ?: throw ApiException(
          ApiError.UNABLE_TO_PARSE_REGISTRATION_REQUEST, "Unable to parse registration request"
        )
      ),
      base64UrlEncode(attestedCredentialData.credentialId),
      base64UrlEncode(
        attestedCredentialData.coseKey.publicKey?.encoded ?: throw ApiException(
          ApiError.UNABLE_TO_PARSE_REGISTRATION_REQUEST, "Unable to parse registration request"
        )
      ),
      passkeyCompressedPublicKeyBase64Url,
      authenticatorData.signCount,
      authenticatorData.isFlagBE,
      authenticatorData.isFlagBS,
      acceptRegistrationRequest.transports
    )

    credential.walletMetadata = WalletMetadata.create(
      credentialPublicKeySignatureBase64Url,
      credentialSignatureOfAccountPublicKeyBase64Url
    )

    val publicKeyBase64Url = base64UrlEncode(acceptRegistrationRequest.accountPublicKey.toPublicKeyBytes())
    val existingWallet = custodialWalletDbService.findPasskeyWalletsByUserAccountId(userAccountId).find {
        it.wallet.publicKeyBase64Url == publicKeyBase64Url
    }?.wallet
    val wallet = if (existingWallet?.id != null) {
      existingWallet
    } else {
      Wallet.create(userAccountId, publicKeyBase64Url)
    }
    custodialWalletDbService.savePasskeyWallet(
      PasskeyWallet(
        credential,
        wallet,
      )
    )
  }

  private suspend fun getCredential(userAccountId: BigInteger, credentialId: String, sessionId: String): PasskeyWallet {
    val userPasskeyWallet = custodialWalletDbService.findPasskeyWalletByCredentialId(credentialId)
    if (userPasskeyWallet == null) {
      //NOTE(Aziz, 10-24-24): The following is some logic for a use case where a passkey exists for this user overall but not for this device
      //We may want to give the user some information on this and act accordingly
      if (walletExistsForAccount(userAccountId)) {
        throw ApiException(
          ApiError.PASSKEY_WALLET_NOT_FOUND_FOR_THIS_DEVICE,
          "A passkey wallet exists for this user $userAccountId on a different device for credentialId=$credentialId, sessionId=$sessionId"
        )
      } else {
        throw ApiException(
          ApiError.PASSKEY_WALLET_NOT_FOUND_FOR_THIS_USER,
          "A passkey wallet does not exist for this user $userAccountId, sessionId=$sessionId"
        )
      }
    } else {
      //Sanity check that the wallet with the given credential id matches the userAccountId given by the session
      if (userPasskeyWallet.wallet.userAccountId != userAccountId) {
        throw ApiException(
          ApiError.PASSKEY_WALLET_CREDENTIAL_ID_DOES_NOT_MATCH_USER_ACCOUNT_ID,
          "A passkey wallet with credential id $credentialId is not associated with the user $userAccountId, sessionId=$sessionId")
      }

      return userPasskeyWallet
    }
  }

  private suspend fun getUserAccountIdOrThrow(sessionId: String): BigInteger{
    return lookupOrchestrationService.findWebsiteOrSiwaAuthenticatedUserDataOrThrow(sessionId).userAccountId
  }

  private suspend fun consolidateCredentialResponseDto(userPasskeyWallet: PasskeyWallet): CredentialResponseDto {
    val userWalletMetadata = getWalletMetadataByCredentialId(userPasskeyWallet.credential.id!!)
    val credentialPublicKeyDto = PublicKeyDto(
      userPasskeyWallet.credential.compressedPublicKeyBase64Url,
      Encoding.BASE64_URLENCODED,
      PublicKeyFormat.COMPRESSED_HEX,
      KeyPairType.PASSKEY_COMPRESSED
    )
    val accountPublicKeyDto = PublicKeyDto(
      userPasskeyWallet.wallet.publicKeyBase64Url,
      Encoding.BASE64_URLENCODED,
      PublicKeyFormat.COMPRESSED_HEX,
      KeyPairType.SR25519
    )
    val credentialPublicKeySignature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.BASE64_URLENCODED,
      userWalletMetadata.signatureOfCredentialPublicKeyBase64Url
    )
    val accountPublicKeySignature = if(userWalletMetadata.credentialSignatureOfAccountPublicKeyBase64Url != null) {
      Signature(
        SignatureKeyPairType.PASSKEY_COMPRESSED,
        Encoding.BASE64_URLENCODED,
        userWalletMetadata.credentialSignatureOfAccountPublicKeyBase64Url!!)
    } else {
      null
    }

    return CredentialResponseDto(
      userPasskeyWallet.credential.credentialIdBase64Url,
      credentialPublicKeyDto,
      accountPublicKeyDto,
      credentialPublicKeySignature,
      accountPublicKeySignature
    )
  }

  //Given that this is for the passkey wallet, it SHOULD have a wallet metadata for a given credential id
  private suspend fun getWalletMetadataByCredentialId(credentialId: BigInteger): WalletMetadata {
    return custodialWalletDbService.findWalletMetadataByCredentialId(credentialId)
      ?: throw ApiException(ApiError.NO_WALLET_METADATA_FOUND_FOR_PASSKEY_WALLET_CREDENTIAL, "No wallet metadata found for this passkey wallet credential")
  }
}

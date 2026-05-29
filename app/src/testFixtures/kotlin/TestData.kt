package io.amplica.custodial_wallet.orchestration.siwa

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import com.strategyobject.substrateclient.crypto.ss58.SS58Codec
import io.amplica.custodial_wallet.EncryptedKeyData
import io.amplica.custodial_wallet.client.kms.EncryptedKey
import io.amplica.custodial_wallet.client.kms.KmsDecryptionKey
import io.amplica.custodial_wallet.client.kms.KmsEncryptionAlgorithm
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.db.repository.UserKeyData
import io.amplica.custodial_wallet.orchestration.SmsProperties
import io.amplica.custodial_wallet.service.key.GeneratedKeyPairData
import io.amplica.custodial_wallet.service.organization.OriginDescriptor
import io.amplica.custodial_wallet.service.provider_metadata.ProviderMetadata
import io.amplica.custodial_wallet.util.key_creation.*
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.custodial_wallet.util.toHex
import io.amplica.custodial_wallet.verifiablecredentials.dto.*
import io.amplica.custodial_wallet.web.Environment
import io.amplica.frequency.util.FrequencyEnvironment
import io.amplica.frequency.util.GraphConfiguration
import io.amplica.frequency.util.GraphHelper
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import kotlin.random.Random
import io.amplica.custodial_wallet.verifiablecredentials.dto.KeyPairType as VCKeyPairType

val graphHelper = GraphHelper(GraphConfiguration(FrequencyEnvironment.ROCOCO, listOf()))

val LOCALE: Locale = Locale.US

val RESEND_DURATION: Duration = Duration.ofSeconds(10)
const val RESEND_LIMIT = 2
const val FREEBEES = 1
const val INCORRECT_ATTEMPTS_LIMIT = 1
val SCHEMA_MAP = mapOf(
  setOf(1, 2, 3) to "permission.one.two.three",
  setOf(4) to "permission.four",
)
const val REDIS_EXPIRATION_MINUTES = 20L

const val SIGN_UP_TEMPLATE_NAME = "webviewSignUpTemplateName"
const val LOGIN_TEMPLATE_NAME = "webviewLoginTemplateName"
val PROPERTIES = DefaultSiwaOrchestrationProperties(
  IdentifierVerificationProperties(RESEND_DURATION, RESEND_LIMIT, FREEBEES, INCORRECT_ATTEMPTS_LIMIT),
  SCHEMA_MAP,
  10L,
  SmsProperties(
    "+15128675309",
    "directLoginTemplate",
    "addIdentifier",
    SIGN_UP_TEMPLATE_NAME,
    LOGIN_TEMPLATE_NAME,
  ),
  SS58AddressFormat.SUBSTRATE_ACCOUNT,
  Duration.ofMinutes(REDIS_EXPIRATION_MINUTES),
  SiwaEmailHandling.OTP,
  false,
  Environment.TEST
)

const val PROVIDER_DISPLAY_NAME = "MeWe"
val PROVIDER_MSA_ID = 78.toBigInteger()
val PROVIDER_PUBLIC_KEY = PublicKeyDto(
  SS58Codec.encode(Random.nextBytes(32), SS58AddressFormat.SUBSTRATE_ACCOUNT),
  Encoding.BASE_58,
  PublicKeyFormat.SS58,
  KeyPairType.SR25519
)

val PROVIDER_METADATA = ProviderMetadata(
  PROVIDER_DISPLAY_NAME,
  PROVIDER_DISPLAY_NAME.lowercase(),
  listOf(OriginDescriptor("http","mewe.com")),
  emptyMap(),
)

const val ALT_PROVIDER_DISPLAY_NAME = "CitizenPortal"
val ALTERNATE_PROVIDER_METADATA = ProviderMetadata(
  ALT_PROVIDER_DISPLAY_NAME,
  ALT_PROVIDER_DISPLAY_NAME.lowercase(),
  listOf(OriginDescriptor("https","citizenportal.ai")),
  emptyMap(),
)
const val CALLBACK_URL = "callback.example.com"
val USER_KEY_PAIR_TYPE = KeyPairType.SR25519 //TODO remove hardcoding when there is ETH support
const val USER_IDENTIFIER_ADMIN_URL = "useradmin.example.com"
const val VERIFIED_CREDENTIAL_URL = "verified.credential.example.com"
val SIGNATURE_REQUEST = SignedSiwaSignatureRequest(
  PROVIDER_PUBLIC_KEY,
  Signature(SignatureKeyPairType.SR25519, Encoding.HEX, "example-signature"),
  SiwaSignatureRequest(CALLBACK_URL, listOf(1, 2, 3, 4), USER_IDENTIFIER_ADMIN_URL),
)
val SIWA_REQUEST = SiwaRequest(
  SIGNATURE_REQUEST,
  listOf(
    RequestedCredential.AnyOf(
      listOf(
        RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedEmailAddressCredential, listOf("???")),
        RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedPhoneNumberCredential, listOf("???"))
      )
    ),
    RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedGraphKeyCredential, listOf("???"))
  ),
  SiwaEmailHandling.OTP,
  ApplicationContext(URI(VERIFIED_CREDENTIAL_URL)),

)

val SIWA_REQUEST_NO_APPLICATION_CONTEXT = SiwaRequest(
  SIGNATURE_REQUEST,
  listOf(
    RequestedCredential.AnyOf(
      listOf(
        RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedEmailAddressCredential, listOf("???")),
        RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedPhoneNumberCredential, listOf("???"))
      )
    ),
    RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedGraphKeyCredential, listOf("???"))
  ),
  SiwaEmailHandling.OTP,
  )

val SIWA_REQUEST_MAGIC_LINK = SiwaRequest(
  SIGNATURE_REQUEST,
  listOf(
    RequestedCredential.AnyOf(
      listOf(
        RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedEmailAddressCredential, listOf("???")),
        RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedPhoneNumberCredential, listOf("???"))
      )
    ),
    RequestedCredential.SpecificCredential(RequestedCredentialType.VerifiedGraphKeyCredential, listOf("???"))
  ),
  SiwaEmailHandling.MAGIC_LINK,
  ApplicationContext(URI(VERIFIED_CREDENTIAL_URL)),
)

val EMAIL_IDENTIFIER = UserIdentifier("john.doe@example.com", UserIdentifierType.EMAIL)
val PLUS_ADDRESSED_EMAIL_IDENTIFIER = UserIdentifier("developers+12345@unfinished.com", UserIdentifierType.EMAIL)
val PLUS_ADDRESSED_EMAIL_IDENTIFIER_NOT_WHITELISTED = UserIdentifier("developers+12345@gmail.com", UserIdentifierType.EMAIL)
val SMS_IDENTIFIER = UserIdentifier("+16268403496", UserIdentifierType.PHONE_NUMBER)

val USER_ACCOUNT_ID = 7.toBigInteger()


const val CURRENT_BLOCK_NUMBER = 9000L

val USER_MSA_ID = 1234.toBigInteger()
val ACCOUNT_KEY_PAIR = Sr25519KeyPairCreator.createKeyPair()
val ACCOUNT_KEY_PAIR_DATA = GeneratedKeyPairData(
  Sr25519KeyPairCreator.createSr25519PublicKeyDto(ACCOUNT_KEY_PAIR, SS58AddressFormat.SUBSTRATE_ACCOUNT),
  ACCOUNT_KEY_PAIR,
  EncryptedKeyData(
    ACCOUNT_KEY_PAIR.publicKeyBytes,
    EncryptedKey(
      "fakeCipherText".toByteArray(),
      KmsDecryptionKey("test-key-id", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
    ),
    KeyPairType.SR25519,
    KeyUsageType.ACCOUNT
  )
)

private val now = Instant.now().toEpochMilli().toBigInteger()

val ACCOUNT_PUBLIC_KEY_HEX = toHex(ACCOUNT_KEY_PAIR.publicKeyBytes)
val ACCOUNT_USER_KEY_DATA = UserKeyData(
  1.toBigInteger(),
  1.toBigInteger(),
  ACCOUNT_PUBLIC_KEY_HEX,
  toHex(ACCOUNT_KEY_PAIR.privateKeyBytes),
  KeyPairType.SR25519,
  "8c67db3ad31b432b86843ea633fca71555bf624e",
  KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
  KeyUsageType.ACCOUNT,
  null,
  now,
  now,
  1.toBigInteger()
)

val GRAPH_KEY_PAIR = X25519KeyPairCreator.createKeyPair()
val GRAPH_KEY_PAIR_DATA = GeneratedKeyPairData(
  X25519KeyPairCreator.createX25519PublicKeyDtoDsnpFormat(graphHelper, GRAPH_KEY_PAIR),
  GRAPH_KEY_PAIR,
  EncryptedKeyData(
    GRAPH_KEY_PAIR.publicKeyBytes,
    EncryptedKey(
      "fakeCipherText".toByteArray(),
      KmsDecryptionKey("test-key-id", KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT)
    ),
    KeyPairType.X25519,
    KeyUsageType.GRAPH
  )
)

val GRAPH_USER_KEY_DATA = UserKeyData(
  1.toBigInteger(),
  1.toBigInteger(),
  toHex(GRAPH_KEY_PAIR.publicKeyBytes),
  toHex(GRAPH_KEY_PAIR.privateKeyBytes),
  KeyPairType.X25519,
  "8c67db3ad31b432b86843ea633fca71555bf624e",
  KmsEncryptionAlgorithm.SYMMETRIC_DEFAULT,
  KeyUsageType.GRAPH,
  null,
  now,
  now,
  1.toBigInteger()
)

const val GRAPH_KEY_SCHEMA_ID = 77
const val DEFAULT_PAGE_HASH = 1492L

val CLAIM_HANDLE_SIGNATURE = Signature(
  SignatureKeyPairType.SR25519,
  Encoding.HEX,
  "bogusClaimHandleSignature"
)
val ADD_PROVIDER_SIGNATURE = Signature(
  SignatureKeyPairType.SR25519,
  Encoding.HEX,
  "bogusAddProviderSignature"
)
val ITEM_ACTIONS_SIGNATURE = Signature(
  SignatureKeyPairType.SR25519,
  Encoding.HEX,
  "bogusItemActionsWithSignatureV2Signature"
)
val LOGIN_SIGNATURE = Signature(
  SignatureKeyPairType.SR25519,
  Encoding.HEX,
  "bogusLoginSignature"
)

val CAIP_122_LOGIN_PAYLOAD_RESPONSE = Caip122LoginPayloadResponse("someMessage")

val EMAIL_VERIFIABLE_CREDENTIAL = VerifiableCredential(
  listOf("context.example.com"),
  listOf(
    CredentialType.VerifiedEmailAddressCredential,
    CredentialType.VerifiableCredential
  ),
  "issuer.example.com",
  ZonedDateTime.now(),
  CredentialSchema.EMAIL_ADDRESS,
  CredentialSubject.Email(
    "did:key:z???",
    EMAIL_IDENTIFIER.value,
    ZonedDateTime.now()
  ),
  Proof(
    ProofType.DataIntegrityProof,
    "example-proof-verification-method",
    "example-cryptosuite",
    ProofPurpose.AssertionMethod,
    "example-proof-value"
  )
)

val GRAPH_KEY_VERIFIABLE_CREDENTIAL = VerifiableCredential(
  listOf("context.example.com"),
  listOf(
    CredentialType.VerifiedGraphKeyCredential,
    CredentialType.VerifiableCredential
  ),
  "issuer.example.com",
  ZonedDateTime.now(),
  CredentialSchema.KEY_PAIR,
  CredentialSubject.KeyPair(
    "did:key:z???",
    toHex(GRAPH_KEY_PAIR.publicKeyBytes),
    toHex(GRAPH_KEY_PAIR.privateKeyBytes),
    KeyPairEncoding.BASE_16,
    KeyPairFormat.BARE,
    VCKeyPairType.X25519,
    DsnpKeyType.PublicKeyKeyAgreement
  ),
  Proof(
    ProofType.DataIntegrityProof,
    "example-proof-verification-method",
    "example-cryptosuite",
    ProofPurpose.AssertionMethod,
    "example-proof-value"
  )
)


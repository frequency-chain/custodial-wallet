package io.amplica.custodial_wallet.siggen

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.strategyobject.substrateclient.common.convert.HexConverter
import com.strategyobject.substrateclient.crypto.KeyPair as SubstrateClientKeyPair
import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.client.redis.dto.*
import io.amplica.custodial_wallet.orchestration.payload.GenerateSmsCodeRequest
import io.amplica.custodial_wallet.orchestration.payload.HandleRequest
import io.amplica.custodial_wallet.orchestration.payload.LoginRequest
import io.amplica.custodial_wallet.orchestration.payload.SignUpRequest
import io.amplica.custodial_wallet.util.SubstrateOrAccountKeyPair
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.SignatureKeyPairType
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairCreator
import io.amplica.custodial_wallet.util.toHex
import io.amplica.frequency.crypto.KeyPair
import io.amplica.frequency.crypto.AccountKeyPair
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.frequency.crypto.toPrivateKeyBytes
import io.amplica.frequency.crypto.toPublicKeyBytes
import io.amplica.frequency.serialization.Environment
import io.amplica.frequency.serialization.FrequencySerializable
import io.amplica.frequency.serialization.JacksonBasedEip712ObjectMapper
import io.amplica.frequency.serialization.SubstrateScaleObjectMapper
import io.amplica.frequency.service.DefaultSigningService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * SignatureGenerator - This class has utility methods used for generating signatures for payloads.
 * These are used in some of our tests, but can also be used for generating signatures for manual testing
 * This can be done by removing the @Disabled from any test and settings the values.
 *
 * Main use for manual testing is the SMS Payload generation for requesting the signature value
 * This was done to prevent the need to start up test containers and speedup the entire process
 */
class SignatureGenerator {

  companion object {
    private const val ALICE_PUBLIC_KEY_PART = "d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"
    private const val ALICE_PRIVATE_KEY_PART = "0x98319d4ff8a9508c4bb0cf0b5a78d760a0b2082c02775e6e82370816fedfff48925a225d97aa00682d6a59b95b18780c10d7032336e88f3442b42361f4a66011"

    //Munge these values if you need to use a different key
    private const val PUBLIC_KEY_PART = ALICE_PUBLIC_KEY_PART
    private const val PRIVATE_KEY_PART = ALICE_PRIVATE_KEY_PART

    private const val KEYPAIR_HEX_ENCODED = PRIVATE_KEY_PART + PUBLIC_KEY_PART

    private val PACKAGES_TO_SCAN = listOf(
      "io.amplica.custodial_wallet.orchestration",
      "io.amplica.frequency.signing_service",
      "io.amplica.frequency.client"
    )
    private val eip712ObjectMapper = JacksonBasedEip712ObjectMapper(jacksonObjectMapper(), Environment.TEST)
    private val scaleObjectMapper = SubstrateScaleObjectMapper(PACKAGES_TO_SCAN)
    private val signingService = DefaultSigningService(scaleObjectMapper, eip712ObjectMapper)


    fun generateAddProviderRequestSignature(request: AddProviderPayloadRequest): Signature {
      val payload = SignUpRequest(request.msaId, request.schemaIds, request.url?.toString())
      return signAlicePayload(payload)
    }

    fun generateHandleRequestSignature(request: HandlePayloadRequest): Signature {
      val scaleRequest = HandleRequest(request.baseHandle)
      return signAlicePayload(scaleRequest)
    }

    fun generateLoginPayloadSignature(payload: LoginPayload): Signature {
      val loginPayloadToSign = LoginRequest(payload.nonce, payload.url?.toString())
      return signAlicePayload(loginPayloadToSign)
    }

    fun generateGenerateSmsCodePayloadSignature(payload: GenerateSmsCodePayload): Signature {
      val generateSmsCodePayloadToSign = GenerateSmsCodeRequest(payload.sessionId)
      return signAlicePayload(generateSmsCodePayloadToSign)
    }

    fun signSiwaPayloadRequest(providerKeyPair: SubstrateOrAccountKeyPair, payload: SiwaSignatureRequest): Signature {
      val siwaPayloadToSign = io.amplica.custodial_wallet.orchestration.payload.SiwaSignatureRequest(
        payload.callback,
        payload.permissions,
        payload.userIdentifierAdminUrl
      )
      return when (providerKeyPair) {
        is SubstrateOrAccountKeyPair.SubstrateKeyPairWrapper -> signPayload(
          KeyPair(
            providerKeyPair.keyPair.asPublicKey().bytes.toPublicKeyBytes(),
            providerKeyPair.keyPair.asSecretKey().bytes.toPrivateKeyBytes(),
            Sr25519CryptoProvider
          ), siwaPayloadToSign
        )
        is SubstrateOrAccountKeyPair.AccountKeyPairWrapper -> signPayload(
          providerKeyPair.keyPair, siwaPayloadToSign
        )
      }
    }

    private fun signPayload(providerKeyPair: AccountKeyPair, payload: FrequencySerializable<Any>): Signature {
      val signature = signingService.signPayload(providerKeyPair, payload)
      return Signature(
        SignatureKeyPairType.SR25519,
        Encoding.HEX,
        toHex(signature.bytes)
      )
    }

    private fun signAlicePayload(payload: FrequencySerializable<Any>): Signature {
      return signPayload(testKeyPair(), payload)
    }

    fun testKeyPair(): AccountKeyPair {
      //if you need to do this from a secret key phrase bytes you need to go through KeyRing to get public and private key bytes
      val keyPair = SubstrateClientKeyPair.fromBytes(HexConverter.toBytes(KEYPAIR_HEX_ENCODED))

      val ss58String = Sr25519KeyPairCreator.encodeSr25519PublicKey(keyPair.asPublicKey().bytes, SS58AddressFormat.SUBSTRATE_ACCOUNT)
      println("SS58=${ss58String}")

      return KeyPair(
        keyPair.asPublicKey().bytes.toPublicKeyBytes(),
        keyPair.asSecretKey().bytes.toPrivateKeyBytes(),
        Sr25519CryptoProvider
      )
    }

    private fun printEncodedValue(payloadType: String, signature: Signature) {
      println(
        "--------------- $payloadType --------------------- " +
            "\n signatureValue: " +
            signature.encodedValue +
            "\n-----------------------------------------------------------------------------"
      )
    }

    @BeforeAll
    @JvmStatic
    fun setUp() {
      val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
      root.level = Level.ERROR
    }
  }

  @Disabled
  @Test
  fun addProviderRequestSignature() {
    val msaId = 1
    val schemaIds = listOf(5,7,8,9,10)
    val url = URI("https://www.mewe.com/authenticate")
    val signedPayload = generateAddProviderRequestSignature(AddProviderPayloadRequest(msaId.toBigInteger(), schemaIds, url))
    printEncodedValue("addProviderRequestSignature", signedPayload)
  }

  @Disabled
  @Test
  fun loginPayloadSignature() {
    val nonce = "hello"
    val url = URI("localhost:8080")
    val signedPayload = generateLoginPayloadSignature(LoginPayload(nonce, url))
    printEncodedValue("loginPayloadSignature", signedPayload)
  }

  @Disabled
  @Test
  fun handleRequestSignature() {
    val handle = "sampleHandle"
    val signedPayload = generateHandleRequestSignature(HandlePayloadRequest(handle))
    printEncodedValue("handleRequestSignature", signedPayload)
  }

  @Disabled
  @Test
  fun smsCodePayloadSignature() {
    val sessionId = "299529f9-de32-4ff0-8cf9-d6e0d91ca462"
    val signedPayload = generateGenerateSmsCodePayloadSignature(GenerateSmsCodePayload(sessionId))
    printEncodedValue("smsCodePayloadSignature", signedPayload)
  }
}



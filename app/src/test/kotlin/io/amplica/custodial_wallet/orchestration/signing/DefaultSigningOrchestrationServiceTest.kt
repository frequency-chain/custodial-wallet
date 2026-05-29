package io.amplica.custodial_wallet.orchestration.signing

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.client.redis.dto.Signature
import io.amplica.custodial_wallet.orchestration.CustodialWalletOrchestrationServiceTest
import io.amplica.custodial_wallet.util.fromHex
import io.amplica.custodial_wallet.util.key_creation.Encoding
import io.amplica.custodial_wallet.util.key_creation.SignatureKeyPairType
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairBytes
import io.amplica.custodial_wallet.util.key_creation.Sr25519KeyPairCreator
import io.amplica.custodial_wallet.util.toHex
import io.amplica.frequency.service.SigningService
import io.amplica.frequency.crypto.HashBytes
import io.amplica.frequency.crypto.SignatureBytes
import io.amplica.frequency.crypto.provider.Sr25519CryptoProvider
import io.amplica.custodial_wallet.orchestration.payload.GenerateSmsCodeRequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.*


class DefaultSigningOrchestrationServiceTest {
  companion object Companion {
    lateinit var mockSigningService: SigningService

    lateinit var signingOrchestrationService: DefaultSigningOrchestrationService

    @BeforeAll
    @JvmStatic
    fun setUpClass() {
      mockSigningService = mock()
      signingOrchestrationService = DefaultSigningOrchestrationService(
        mockSigningService,
        SS58AddressFormat.SUBSTRATE_ACCOUNT,
      )
    }
  }

  @ParameterizedTest
  @CsvSource(value = [
    "true",
    "false",
  ])
  fun verifyGenerateSmsCodeSignatureSuccess(isLegit: Boolean) {
    // GIVEN
    val keyPair = Sr25519CryptoProvider.createKeyPairFromSeed(
      fromHex("0xfb798a3ca3411d42d57376ac272f63643ce4873916e6e62ef80bb0daf33ad8fa")
    )
    val publicKeyDto = Sr25519KeyPairCreator.createSr25519PublicKeyDto(
      Sr25519KeyPairBytes(
        keyPair.publicKeyBytes.bytes,
        keyPair.privateKeyBytes.bytes,
      ),
      SS58AddressFormat.SUBSTRATE_ACCOUNT
    )
    val digest = fromHex("0x1234")
    val signatureBytes = keyPair.sign(HashBytes(digest))
    val signature = Signature(
      SignatureKeyPairType.SR25519,
      Encoding.HEX,
      toHex(signatureBytes.bytes),
    )
    val request = GenerateSmsCodeRequest(
      CustodialWalletOrchestrationServiceTest.sessionId
    )

    whenever(
      mockSigningService.verifySignedPayload(
        argThat { this.publicKeyBytes.contentEquals(keyPair.publicKeyBytes) },
        eq(request),
        any<SignatureBytes>() // value classes aren't supported by mockito outside of `any()`
      )
    ).thenReturn(isLegit)

    // WHEN
    val result = signingOrchestrationService.verifySignedPayload(publicKeyDto, request, signature)

    // THEN
    Assertions.assertThat(result).isEqualTo(isLegit)
  }

}

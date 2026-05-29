package io.amplica.custodial_wallet.service.ics

import io.amplica.custodial_wallet.util.fromHex
import io.amplica.frequency.client.FrequencyClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.mock

@OptIn(ExperimentalStdlibApi::class)
class JavaSdkIcsServiceTest {

  private val mockFrequencyClient: FrequencyClient = mock()
  private val service = JavaSdkIcsService(mockFrequencyClient)

  @Test
  fun generateMasterMnemonicSeedPhraseSucceeds() {
    // WHEN
    val result = service.generateMasterMnemonicSeedPhrase()

    // THEN
    Assertions.assertThat(result.split(' ')).hasSize(24)
  }

  @Test
  fun deriveMasterSeedSucceeds() {
    // GIVEN
    val mnemonicPhrase =
      "fall enforce arctic delay save twelve kid globe ghost wish relax mobile improve fitness school boil neutral bag twist wonder rough nice under copper"

    // WHEN
    val result = service.deriveMasterSeed(mnemonicPhrase)

    // THEN
    val expected =
      "9b26c0e23751261ffc42099d3346989e0c459eb57668d1019407c79f23f9c49fd402934fac6429a731a2a4e56a85807926b460d5912ed929196d4c83b17dd27a"
    Assertions.assertThat(result.toHexString()).isEqualTo(expected)
  }

  @Test
  fun deriveUserRootIcsKeyPairFromSeedSucceeds() {
    // GIVEN
    val seed =
      "9b26c0e23751261ffc42099d3346989e0c459eb57668d1019407c79f23f9c49fd402934fac6429a731a2a4e56a85807926b460d5912ed929196d4c83b17dd27a".hexToByteArray()

    // WHEN
    val keyPair = service.deriveMasterKeyPair(seed)

    // THEN
    val expectedPublicKey = "e318f6209b65daf5389ed398396dd1c61c2b3f38b42bb108f86e2591f388d68e"
    val expectedPrivateKey =
      "9b26c0e23751261ffc42099d3346989e0c459eb57668d1019407c79f23f9c49fe318f6209b65daf5389ed398396dd1c61c2b3f38b42bb108f86e2591f388d68e"
    Assertions.assertThat(keyPair.publicKey.toHexString()).isEqualTo(expectedPublicKey)
    Assertions.assertThat(keyPair.privateKey.toHexString()).isEqualTo(expectedPrivateKey)
    Assertions.assertThat(keyPair.type).isEqualTo(IcsKeyType.ED25519)
  }

  @ParameterizedTest
  @CsvSource(value = [
    "0",
    "1",
    "89",
    "18446744073709551615" // uint64 max
  ])
  fun encryptProviderMsaIdDecryptsSuccessfully(msaIdString: String) {
    // GIVEN
    val userOnChainKey = fromHex("0x5a5095b9a39851cf4d28bebaaef8c423f000368b5c0ed2a9b263d926781e152c")
    val msaId = msaIdString.toBigInteger()

    // WHEN
    val encryptedResult = service.encryptProviderMsaId(userOnChainKey, msaId)

    // THEN
    val decryptedResult = service.decryptProviderMsaId(userOnChainKey, encryptedResult)

    Assertions.assertThat(decryptedResult).isEqualTo(msaId)
  }

}

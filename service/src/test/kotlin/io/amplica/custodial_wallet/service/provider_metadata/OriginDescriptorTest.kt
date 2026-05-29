package io.amplica.custodial_wallet.service.provider_metadata

import io.amplica.custodial_wallet.service.organization.OriginDescriptor
import org.assertj.core.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI

class OriginDescriptorTest {

  @ParameterizedTest
  @CsvSource(
    value = [
      "https, amplica.io, https://amplica.io/some/path, true",
      "https, amplica.io, https://testnet.amplica.io/some/path, true",
      "https, amplica.io, http://testnet.amplica.io/some/path, false",
      "odessa, login, odessa://login/some/path, true",
      "odessa, malicious.login, odessa://login/some/path, false",
      "https, amplica.io, odessa://login, false",
    ]
  )
  fun matches(scheme: String, domain: String, urlToCheck: String, expectedResult: Boolean) {
    Assertions.assertThat(OriginDescriptor(scheme, domain).matches(URI(urlToCheck))).isEqualTo(expectedResult)
  }

}
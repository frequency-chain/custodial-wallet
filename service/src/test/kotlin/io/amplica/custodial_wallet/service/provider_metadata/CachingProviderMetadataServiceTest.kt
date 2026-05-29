package io.amplica.custodial_wallet.service.provider_metadata

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.net.URI
import java.time.Duration


class CachingProviderMetadataServiceTest {

  private val mockDelegate: ProviderMetadataService = mock()

  private val cachedService = CachingProviderMetadataService(
    mockDelegate,
    Duration.ofDays(1), // Attempting to avoid any timing issues during test execution
    2
  )

  @Test
  fun resolveProviderMetadata(): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    val metadata = ProviderMetadata("X: The Everything App", "twitter", emptyList(), emptyMap())
    whenever(mockDelegate.resolveProviderMetadata(eq(msaId))).thenReturn(metadata)

    // WHEN
    val result = cachedService.resolveProviderMetadata(msaId)

    // THEN
    Assertions.assertThat(result).isEqualTo(metadata)
  }

  @Test
  fun resolveProviderMetadataRepeatReads(): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    val metadata = ProviderMetadata("X: The Everything App", "twitter", emptyList(), emptyMap())
    whenever(mockDelegate.resolveProviderMetadata(eq(msaId))).thenReturn(metadata)

    // WHEN
    val first = cachedService.resolveProviderMetadata(msaId)
    val second = cachedService.resolveProviderMetadata(msaId)

    // THEN
    Assertions.assertThat(first).isEqualTo(metadata)
    Assertions.assertThat(second).isEqualTo(metadata)

    // Delegate only invoked once
    verify(mockDelegate, times(1)).resolveProviderMetadata(any())
  }

  @Test
  fun resolveProviderMetadataNoMatch(): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    whenever(mockDelegate.resolveProviderMetadata(eq(msaId))).thenReturn(null)

    // WHEN
    val result = cachedService.resolveProviderMetadata(msaId)

    // THEN
    Assertions.assertThat(result).isEqualTo(null)
  }

  @Test
  fun resolveProviderMetadataException(): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    val errorMessage = "Contrived error from database"
    whenever(mockDelegate.resolveProviderMetadata(eq(msaId))).thenThrow(RuntimeException(errorMessage))

    // WHEN
    Assertions.assertThatThrownBy {
      runBlocking {
        cachedService.resolveProviderMetadata(msaId)
      }
    }.isInstanceOf(RuntimeException::class.java).hasMessage(errorMessage)
  }

  @Test
  fun resolveProviderMetadataForApplication(): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    val verifiedCredentialUrl = URI.create("www.example.com")
    val metadata = ProviderMetadata("X: The Everything App", "twitter", emptyList(), emptyMap())
    whenever(mockDelegate.resolveProviderMetadataForApplication(eq(msaId), eq(verifiedCredentialUrl))).thenReturn(metadata)

    // WHEN
    val result = cachedService.resolveProviderMetadataForApplication(msaId, verifiedCredentialUrl)

    // THEN
    Assertions.assertThat(result).isEqualTo(metadata)
  }

  @Test
  fun resolveProviderMetadataForApplicationRepeatReads(): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    val verifiedCredentialUrl = URI.create("www.example.com")
    val metadata = ProviderMetadata("X: The Everything App", "twitter", emptyList(), emptyMap())
    whenever(mockDelegate.resolveProviderMetadataForApplication(eq(msaId), eq(verifiedCredentialUrl))).thenReturn(metadata)

    // WHEN
    val first = cachedService.resolveProviderMetadataForApplication(msaId, verifiedCredentialUrl)
    val second = cachedService.resolveProviderMetadataForApplication(msaId, verifiedCredentialUrl)

    // THEN
    Assertions.assertThat(first).isEqualTo(metadata)
    Assertions.assertThat(second).isEqualTo(metadata)

    // Delegate only invoked once
    verify(mockDelegate, times(1)).resolveProviderMetadataForApplication(any(), any())
  }

  @Test
  fun resolveProviderMetadataForApplicationNoMatch(): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    val verifiedCredentialUrl = URI.create("www.example.com")
    whenever(mockDelegate.resolveProviderMetadataForApplication(eq(msaId), eq(verifiedCredentialUrl))).thenReturn(null)

    // WHEN
    val result = cachedService.resolveProviderMetadataForApplication(msaId, verifiedCredentialUrl)

    // THEN
    Assertions.assertThat(result).isEqualTo(null)
  }

  @Test
  fun resolveProviderMetadataForApplicationException(): Unit = runBlocking {
    // GIVEN
    val msaId = 1.toBigInteger()
    val verifiedCredentialUrl = URI.create("www.example.com")
    val errorMessage = "Contrived error from database"
    whenever(mockDelegate.resolveProviderMetadataForApplication(eq(msaId), eq(verifiedCredentialUrl))).thenThrow(RuntimeException(errorMessage))

    // WHEN
    Assertions.assertThatThrownBy {
      runBlocking {
        cachedService.resolveProviderMetadataForApplication(msaId, verifiedCredentialUrl)
      }
    }.isInstanceOf(RuntimeException::class.java).hasMessage(errorMessage)
  }
}
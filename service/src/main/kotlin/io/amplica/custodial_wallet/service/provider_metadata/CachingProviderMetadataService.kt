package io.amplica.custodial_wallet.service.provider_metadata

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.math.BigInteger
import java.net.URI
import java.time.Duration


class CachingProviderMetadataService(
  private val delegate: ProviderMetadataService,
  timeToLive: Duration,
  cacheSize: Int
) : ProviderMetadataService {

  private val providerMetadataCacheLoader = object : CacheLoader<BigInteger, Deferred<ProviderMetadata?>>() {
    override fun load(msaId: BigInteger): Deferred<ProviderMetadata?> {
      // Bridge our `suspend`ed implementation into a synchronous execution that returns a `Deferred` result
      return CoroutineScope(Dispatchers.IO).async {
        delegate.resolveProviderMetadata(msaId)
      }
    }
  }

  private val providerMetadataForApplicationCacheLoader = object : CacheLoader<Pair<BigInteger, URI>, Deferred<ProviderMetadata?>>() {
    override fun load(key: Pair<BigInteger, URI>): Deferred<ProviderMetadata?> {
      // Bridge our `suspend`ed implementation into a synchronous execution that returns a `Deferred` result
      return CoroutineScope(Dispatchers.IO).async {
        delegate.resolveProviderMetadataForApplication(key.first, key.second)
      }
    }
  }

  private val cacheBuilder = CacheBuilder.newBuilder()
    .expireAfterWrite(timeToLive)
    .maximumSize(cacheSize.toLong())

  private val providerMetadataCache = cacheBuilder.build(providerMetadataCacheLoader)
  private val providerMetadataForApplicationCache = cacheBuilder.build(providerMetadataForApplicationCacheLoader)

  @Deprecated("Use ", replaceWith = ReplaceWith("resolveProviderMetadataForApplication(msaId, verifiedCredentialUrl)"))
  override suspend fun resolveProviderMetadata(msaId: BigInteger): ProviderMetadata? {
    return providerMetadataCache.get(msaId).await()
  }

  override suspend fun resolveProviderMetadataForApplication(
    msaId: BigInteger,
    verifiedCredentialUrl: URI
  ): ProviderMetadata? {
    return providerMetadataForApplicationCache.get(Pair(msaId, verifiedCredentialUrl)).await()
  }

}

package io.amplica.custodial_wallet.controller.util

import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.util.key_creation.PublicKeyDto
import org.springframework.http.HttpHeaders
import java.util.*

data class SessionIdHolder(val sessionId: String)

data class BooleanHolder(val response: Boolean)

data class RebuildSignupPayloadRequest(val externalUserId: String, val providerPublicKey: PublicKeyDto, val handle: String)

data class RebuildSignupPayloadRequestByPublicKey(val providerPublicKey: PublicKeyDto, val userPublicKey: PublicKeyDto, val handle: String)

data class ChangeExternalUserIdRequest(val providerPublicKey: PublicKeyDto, val userPublicKey: PublicKeyDto, val desiredExternalUserId: String)

data class PublicKeyAndChainStateRequest(val providerPublicKey: PublicKeyDto, val userIdentifier: UserIdentifier)

data class PublicKeyAndChainStateResponse(val userPublicKey: PublicKeyDto, val existsOnChain: Boolean)

fun getPermissionKeysForSchemaIds(
  schemaIds: List<Int>,
  schemaIdPermissionsMap: Map<Set<Int>, String>,
  prefix: String?
): List<String> {
  return schemaIdPermissionsMap.flatMap { (schemaIdsForPermission, permission) ->
    when {
      schemaIds.containsAll(schemaIdsForPermission) -> {
        listOf(resolvePermissionName(prefix, permission))
      }

      schemaIdsForPermission.any { schemaIds.contains(it) } -> {
        // NOTE(Julian, 2024-08-01): It is not acceptable for a provider to request a (strict) subset of keys which we
        // group together under a single message key (e.g., some but not all graph permissions).
        throw ApiException(ApiError.INVALID_SCHEMA_IDS, "Invalid Schema IDs: $schemaIds")
      }

      else -> emptyList()
    }
  }
}

fun getPermissionsForSchemaIds(schemaIds: List<Int>, schemaIdPermissionsMap: Map<Set<Int>,String>, locale: Locale, prefix: String?): List<String> {
  return getPermissionKeysForSchemaIds(schemaIds, schemaIdPermissionsMap, prefix)
    .map { ResourceBundle.getBundle("messages",locale).getString(it) }
}

fun resolvePermissionName(prefix: String?, permissionKey: String): String {
  return if(prefix != null) {
    "${prefix}.${permissionKey}"
  }else{
    permissionKey
  }
}

fun getXForwardedForHeader(requestHeaders: HttpHeaders): String? {
  return requestHeaders.getOrEmpty("X-Forwarded-For").firstOrNull()?.ifEmpty { null }
}

/**
 * Fishes out the first (client) IP address from the `X-Forwarded-For` header
 */
fun getClientIpAddress(requestHeaders: HttpHeaders): String? {
  return getXForwardedForHeader(requestHeaders)?.let { forwardedFor ->
    when {
      // NOTE(Julian, 2025-07-24): The first IP is the client (see https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/X-Forwarded-For)
      forwardedFor.contains(",") -> forwardedFor.split(",").first().trim()
      else -> forwardedFor
    }
  }
}

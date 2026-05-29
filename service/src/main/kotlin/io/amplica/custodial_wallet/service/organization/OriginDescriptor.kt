package io.amplica.custodial_wallet.service.organization

import com.google.common.net.InternetDomainName
import java.net.URI

/**
 * Describes the scheme and top private domain (or exact host for non-http(s) schemes) being sought.
 *
 * E.g.,
 * - OriginDescriptor("https", "amplica.io") would match "https://testnet.amplica.io", and
 * - OriginDescriptor("odessa", "login") would match "odessa://login"
 */
data class OriginDescriptor(
  val scheme: String,
  val domain: String
) {

  fun matches(url: URI): Boolean {
    return when {
      url.scheme != scheme -> false
      else -> {
        val urlDomainToCheck = when (url.scheme) {
          "http", "https" -> InternetDomainName.from(url.host).topPrivateDomain().toString()
          else -> url.host
        }

        urlDomainToCheck == domain
      }
    }
  }

  fun baseURL(): URI {
    return URI(scheme, domain, null, null)
  }
  
}

package io.amplica.custodial_wallet.service.whitelist_checker

import com.google.common.net.InternetDomainName

/*
A Whitelist Checker that pulls whitelisted data from configuration files
 */
class ConfigWhitelistChecker(
  private val plusAddressingWhitelistActive: Boolean,
  private val plusAddressList: Set<String>,
  private val loadCapacityEmailTestingEnabled: Boolean,
  private val loadCapacityEmailTestingDomains: Set<String>
) : WhitelistChecker  {

  override fun plusAddressingPermitted(email: String): Boolean {
    if(!plusAddressingWhitelistActive) return true

    val domain = email.substringAfterLast("@" , "").substringBefore(":")

    if(plusAddressList.contains("localhost") && domain == "localhost") return true

    val domainName = InternetDomainName.from(domain).topPrivateDomain().toString()
    return plusAddressList.contains(domainName)
  }

  override fun isNoSendEmailAddress(email: String): Boolean {
    val emailDomain = email.substringAfterLast("@" , "").substringBefore(":")
    if(emailDomain == "") return false
    return emailDomain in loadCapacityEmailTestingDomains && loadCapacityEmailTestingEnabled
  }
}
package io.amplica.custodial_wallet.service.whitelist_checker


interface WhitelistChecker {
  fun plusAddressingPermitted(email: String): Boolean
  fun isNoSendEmailAddress(email: String): Boolean
}
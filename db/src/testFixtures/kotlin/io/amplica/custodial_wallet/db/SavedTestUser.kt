package io.amplica.custodial_wallet.db

import io.amplica.custodial_wallet.db.repository.*


data class SavedTestUser(
  val userAccount: UserAccount,
  val accountUserKeyData: UserKeyData,
  val graphUserKeyData: UserKeyData,
  val providerExternalUser: ProviderExternalUser,
  val userIdentifier: UserIdentifier,
  val userAccountUserIdentifier: UserAccountUserIdentifier,
  val providerExternalUserDetail: ProviderExternalUserDetail,
)

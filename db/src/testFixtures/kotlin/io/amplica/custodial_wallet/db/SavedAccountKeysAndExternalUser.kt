package io.amplica.custodial_wallet.db

import io.amplica.custodial_wallet.db.repository.*

data class SavedAccountKeysAndExternalUser(
  val userAccount: UserAccount,
  val accountUserKeyData: UserKeyData,
  val graphUserKeyData: UserKeyData,
  val providerExternalUser: ProviderExternalUser,
) {
  fun toSavedTestUser(
    userIdentifier: UserIdentifier,
    userAccountUserIdentifier: UserAccountUserIdentifier,
    providerExternalUserDetail: ProviderExternalUserDetail
  ): SavedTestUser {
    return SavedTestUser(
      this.userAccount,
      this.accountUserKeyData,
      this.graphUserKeyData,
      this.providerExternalUser,
      userIdentifier,
      userAccountUserIdentifier,
      providerExternalUserDetail
    )
  }
}
package io.amplica.custodial_wallet.dto

import io.amplica.custodial_wallet.db.repository.UserDetail
import java.math.BigInteger

data class AccountInfo(
    val userDetails: List<UserDetail>,
    val providerUserInfo: List<ProviderUserInfo>,
    val hasPasskeyWallet: Boolean,
)

data class ProviderUserInfo(
    val userAccountId: BigInteger,
    val publicKeyHex: String,
    val providerMsaId: BigInteger,
    val providerExternalId: String,
    val providerExternalUserDetailList: List<UserDetail>,
    val providerName: String,
    val userHandle: String,
    val userMsaId: BigInteger,
    val permissions: List<String> = emptyList(),
    val providerExternalUserId: BigInteger,
    val hasPassword: Boolean,
)

data class GetHandleResponse(
    val baseHandle: String,
    val canonicalHandle: String,
    val handleSuffix: Int,
)

data class AddIdentifierVerificationResponse(
    val isVerified: Boolean,
    val callbackUrl: String?,
    val accountInfo: AccountInfo
)

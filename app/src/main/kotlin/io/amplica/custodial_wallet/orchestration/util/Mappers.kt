package io.amplica.custodial_wallet.orchestration.util

import com.google.common.collect.FluentIterable
import com.google.common.collect.ImmutableListMultimap
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifier
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierResponse
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.db.repository.UserData
import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.db.repository.UserDetailType
import java.math.BigInteger

fun mapUserDetailsToProviderName(userDataList: List<UserData>): ImmutableListMultimap<BigInteger, Pair<BigInteger, UserDetail>> {
  val multiMap: List<Pair<BigInteger, UserDetail>> = userDataList.map { u -> Pair(u.providerMsaId, UserDetail(u.userDetailValue, u.userDetailType, u.userDetailPriority))}
  return FluentIterable.from(multiMap).index { it!!.first }
}

fun mapUserIdentifierToUserDetail(userIdentifier: UserIdentifier): UserDetail {
  return UserDetail(userIdentifier.value, UserDetailType.valueOf(userIdentifier.type.name))
}

fun mapUserIdentifiersToUserDetails(userIdentifiers: List<UserIdentifier>) : List<UserDetail> {
  return userIdentifiers.map { userIdentifier ->
    mapUserIdentifierToUserDetail(userIdentifier)
  }
}

fun mapUserIdentifiersToUserIdentifiersResponse(userIdentifiers: List<UserIdentifier>) : List<UserIdentifierResponse> {
  val userIdentifiersResponse: List<UserIdentifierResponse> =
    userIdentifiers.map { userIdentifier ->
      UserIdentifierResponse(userIdentifier.value, UserIdentifierType.valueOf(userIdentifier.type.name))
    }

  return userIdentifiersResponse
}
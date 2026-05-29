package io.amplica.custodial_wallet.service.password

import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.db.repository.UserPassword
import java.math.BigInteger

interface PasswordService {
  suspend fun checkPasswordExistsByUserAccountId(userAccountId: BigInteger): Boolean
  suspend fun savePasswordByUserAccountId(userAccountId: BigInteger, rawPassword: String): UserPassword
  suspend fun authenticateByUserAccountId(userAccountId: BigInteger, rawPassword: String): Boolean
  suspend fun authenticateByPublicKeyHex(publicKeyHex: String, rawPassword: String): Boolean
  suspend fun authenticateByProviderMsaIdAndProviderExternalId(
    providerMsaId: BigInteger,
    providerExternalId: String,
    rawPassword: String,
  ): Boolean
  suspend fun authenticateByContactMethod(
    contactMethod: String,
    contactMethodType: UserIdentifierType,
    rawPassword: String,
  ): Boolean
  suspend fun updatePasswordByUserAccountId(userAccountId: BigInteger, rawPassword: String): UserPassword
}
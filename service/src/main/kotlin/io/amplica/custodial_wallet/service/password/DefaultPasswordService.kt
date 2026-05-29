package io.amplica.custodial_wallet.service.password

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.db.repository.KeyDerivationAlgorithmType
import io.amplica.custodial_wallet.db.repository.UserDetail
import io.amplica.custodial_wallet.db.repository.UserDetailType
import io.amplica.custodial_wallet.db.repository.UserPassword
import io.amplica.custodial_wallet.exception.ApiError
import io.amplica.custodial_wallet.exception.ApiException
import io.amplica.custodial_wallet.service.password.util.BCryptPasswordChecker
import io.amplica.custodial_wallet.service.password.util.PasswordChecker
import io.amplica.custodial_wallet.service.password.util.PasswordEncoder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.math.BigInteger

open class DefaultPasswordService(
  private val databaseService: CustodialWalletDatabaseService,
  private val encoder: PasswordEncoder,
  private val encoderAlgorithmType: KeyDerivationAlgorithmType,
  private val transactionalOperator: TransactionalOperator
) : PasswordService {

  companion object {
    private val LOG: Logger = LoggerFactory.getLogger(DefaultPasswordService::class.java)

    private fun getChecker(kda: KeyDerivationAlgorithmType): PasswordChecker = when (kda) {
      KeyDerivationAlgorithmType.BCRYPT -> BCryptPasswordChecker
    }

    private fun matches(userPassword: UserPassword, rawPassword: String): Boolean {
      val checker = getChecker(userPassword.keyDerivationAlgorithmType)
      return checker.matches(userPassword.hash, rawPassword)
    }
  }

  override suspend fun checkPasswordExistsByUserAccountId(userAccountId: BigInteger): Boolean {
    return databaseService.findOneUserPasswordByUserAccountId(userAccountId) != null
  }

  override suspend fun savePasswordByUserAccountId(
    userAccountId: BigInteger,
    rawPassword: String
  ): UserPassword  = transactionalOperator.executeAndAwait{
    val hash = encoder.encode(rawPassword)
    val userPassword = UserPassword.create(userAccountId, encoderAlgorithmType, hash)
    databaseService.saveUserPassword(userPassword)
  }

  override suspend fun updatePasswordByUserAccountId(
    userAccountId: BigInteger,
    rawPassword: String
  ): UserPassword = transactionalOperator.executeAndAwait{
    val existingUserPassword = databaseService.findOneUserPasswordByUserAccountId(userAccountId) ?: throw ApiException(
      ApiError.NO_PASSWORD_FOUND,
      "An existing password could not be found to update"
    )
    val hash = encoder.encode(rawPassword)
    val updatedUserPassword = UserPassword.update(existingUserPassword, encoderAlgorithmType, hash)
    databaseService.saveUserPassword(updatedUserPassword)
  }

  override suspend fun authenticateByUserAccountId(userAccountId: BigInteger, rawPassword: String): Boolean {
    val userPassword = databaseService.findOneUserPasswordByUserAccountId(userAccountId)
    return userPassword != null && matches(userPassword, rawPassword)
  }

  override suspend fun authenticateByPublicKeyHex(publicKeyHex: String, rawPassword: String): Boolean {
    val userPassword = databaseService.findOneUserPasswordByPublicKeyHex(publicKeyHex)
    return userPassword != null && matches(userPassword, rawPassword)
  }

  override suspend fun authenticateByProviderMsaIdAndProviderExternalId(
    providerMsaId: BigInteger,
    providerExternalId: String,
    rawPassword: String,
  ): Boolean {
    val userPassword = databaseService.findOneUserPasswordByProviderMsaIdAndProviderExternalId(
      providerMsaId, providerExternalId
    )
    return userPassword != null && matches(userPassword, rawPassword)
  }

  override suspend fun authenticateByContactMethod(
    contactMethod: String,
    contactMethodType: UserIdentifierType,
    rawPassword: String
  ): Boolean {
    val userDetailType = UserDetailType.fromUserIdentifierType(contactMethodType)

    val userAccount = databaseService.findUserAccountByUserIdentifier(
      UserDetail(contactMethod, userDetailType),
    )

    val accountId = when (userAccount) {
      null -> throw ApiException(ApiError.NO_USER_FOUND_ERROR, "No user was found for the given contact method")
      else -> userAccount.id!!
    }

    return authenticateByUserAccountId(accountId, rawPassword)
  }
}
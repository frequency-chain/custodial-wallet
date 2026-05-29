
package io.amplica.custodial_wallet.service.password

import io.amplica.custodial_wallet.ReactiveCustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.dto.UserIdentifierType
import io.amplica.custodial_wallet.db.repository.KeyDerivationAlgorithmType
import io.amplica.custodial_wallet.db.repository.UserAccount
import io.amplica.custodial_wallet.db.repository.UserPassword
import io.amplica.custodial_wallet.service.password.util.BCryptPasswordEncoder
import io.amplica.custodial_wallet.service.password.util.PasswordEncoder
import io.amplica.custodial_wallet.service.util.createTransactionalOperatorDouble
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*
import java.util.stream.Stream

class DefaultPasswordServiceTest {
  companion object {
    const val RAW_PASSWORD = "correct_horse_battery_staple"
    const val PUBLIC_KEY_HEX = "123ABC"
    val USER_ACCOUNT_ID = 1.toBigInteger()

    private fun getTestEncoder(kda: KeyDerivationAlgorithmType): PasswordEncoder {
      return when (kda) {
        KeyDerivationAlgorithmType.BCRYPT -> BCryptPasswordEncoder(4) // Use unsafe level of complexity to speed up tests
      }
    }

    @JvmStatic
    fun kdaCrossProductSource(): Stream<Arguments> {
      val kdaEntries = KeyDerivationAlgorithmType.entries.toTypedArray()

      return Arrays.stream(kdaEntries).flatMap { firstKda ->
        Arrays.stream(kdaEntries).map { secondKda -> Arguments.of(firstKda, secondKda) }
      }
    }
  }

  private val databaseService: ReactiveCustodialWalletDatabaseService = mock()

  @ParameterizedTest
  // The service may be configured to use any KDA, and the database layer may return an encoded value from any KDA
  @MethodSource("kdaCrossProductSource")
  fun savePasswordByUserAccountId(serviceKda: KeyDerivationAlgorithmType, dbKda: KeyDerivationAlgorithmType) {
    runBlocking {
      // GIVEN
      val service = DefaultPasswordService(databaseService, getTestEncoder(serviceKda), serviceKda, createTransactionalOperatorDouble())
      val userPasswordRecord = createTestUserPassword(dbKda, RAW_PASSWORD)

      // Match on important fields in the created UserPassword to make sure create is generating accurate data
      whenever(databaseService.saveUserPassword(
        argThat { userPassword ->
          userPassword.userAccountId == USER_ACCOUNT_ID && userPassword.keyDerivationAlgorithmType == dbKda
        }
      )).thenReturn(userPasswordRecord)

      // WHEN
      val savedPassword = service.savePasswordByUserAccountId(USER_ACCOUNT_ID, RAW_PASSWORD)

      // THEN
      Assertions.assertEquals(userPasswordRecord, savedPassword)
    }
  }

  @ParameterizedTest
  @MethodSource("kdaCrossProductSource")
  fun updatePasswordByUserAccountId(serviceKda: KeyDerivationAlgorithmType, dbKda: KeyDerivationAlgorithmType) {
    runBlocking {
      // GIVEN
      val passwordEncoder = getTestEncoder(serviceKda)
      val service = DefaultPasswordService(databaseService, passwordEncoder, serviceKda, createTransactionalOperatorDouble())

      // Mock the data stored in the database
      val originalRecord = createTestUserPassword(dbKda, RAW_PASSWORD)
      whenever(databaseService.findOneUserPasswordByUserAccountId(eq(USER_ACCOUNT_ID))).thenReturn(
        originalRecord
      )

      // Mock saving
      whenever(databaseService.saveUserPassword(
        argThat { userPassword -> userPassword != null && userPassword.userAccountId == USER_ACCOUNT_ID }
      )).thenAnswer { record -> record }

      // WHEN
      service.updatePasswordByUserAccountId(USER_ACCOUNT_ID, RAW_PASSWORD)

      // THEN
      // The `find` method was invoked
      verify(databaseService, times(1)).findOneUserPasswordByUserAccountId(eq(USER_ACCOUNT_ID))
      // The database service `save` method was invoked correctly
      verify(databaseService, times(1)).saveUserPassword(argThat { updatedRecord ->
        Assertions.assertEquals(originalRecord.id, updatedRecord.id)
        Assertions.assertEquals(USER_ACCOUNT_ID, updatedRecord.userAccountId)
        Assertions.assertEquals(serviceKda, updatedRecord.keyDerivationAlgorithmType)
        Assertions.assertEquals(1.toBigInteger(), updatedRecord.version)
        Assertions.assertEquals(originalRecord.createdAt, updatedRecord.createdAt)
        Assertions.assertTrue(updatedRecord.lastModified > originalRecord.lastModified)

        true
      })
    }
  }

  @ParameterizedTest
  // The service may be configured to use any KDA, and the database layer may return an encoded value from any KDA
  @MethodSource("kdaCrossProductSource")
  fun authenticateByPublicKeyHex(serviceKda: KeyDerivationAlgorithmType, dbKda: KeyDerivationAlgorithmType) {
    runBlocking {
      // GIVEN
      val service = DefaultPasswordService(databaseService, getTestEncoder(serviceKda), serviceKda, createTransactionalOperatorDouble())

      // Mock the data stored in the database
      val userPasswordRecord = createTestUserPassword(dbKda, RAW_PASSWORD)
      whenever(databaseService.findOneUserPasswordByPublicKeyHex(eq(PUBLIC_KEY_HEX))).thenReturn(
        userPasswordRecord
      )

      // WHEN
      val correctPasswordAccepted = service.authenticateByPublicKeyHex(PUBLIC_KEY_HEX, RAW_PASSWORD)
      val incorrectPasswordAccepted = service.authenticateByPublicKeyHex(PUBLIC_KEY_HEX, RAW_PASSWORD.reversed())

      // THEN
      Assertions.assertTrue(correctPasswordAccepted)
      Assertions.assertFalse(incorrectPasswordAccepted)
    }
  }

  @ParameterizedTest
  @MethodSource("kdaCrossProductSource")
  fun authenticateByProviderMsaIdAndProviderExternalId(
    serviceKda: KeyDerivationAlgorithmType,
    dbKda: KeyDerivationAlgorithmType,
  ) {
    runBlocking {
      // GIVEN
      val providerMsaId = 1.toBigInteger()
      val providerExternalId = "teddy"

      val service = DefaultPasswordService(databaseService, getTestEncoder(serviceKda), serviceKda, createTransactionalOperatorDouble())

      // Mock the data stored in the database
      val userPasswordRecord = createTestUserPassword(dbKda, RAW_PASSWORD)
      whenever(
        databaseService.findOneUserPasswordByProviderMsaIdAndProviderExternalId(
          eq(providerMsaId),
          eq(providerExternalId),
        )
      ).thenReturn(
        userPasswordRecord
      )

      // WHEN
      val correctPasswordAccepted = service.authenticateByProviderMsaIdAndProviderExternalId(
        providerMsaId,
        providerExternalId,
        RAW_PASSWORD
      )
      val incorrectPasswordAccepted = service.authenticateByProviderMsaIdAndProviderExternalId(
        providerMsaId,
        providerExternalId,
        RAW_PASSWORD.reversed()
      )

      // THEN
      Assertions.assertTrue(correctPasswordAccepted)
      Assertions.assertFalse(incorrectPasswordAccepted)
    }
  }

  @ParameterizedTest
  @MethodSource("kdaCrossProductSource")
  fun authenticateByContactMethod(serviceKda: KeyDerivationAlgorithmType, dbKda: KeyDerivationAlgorithmType) {
    runBlocking {
      // GIVEN
      val contactMethod = "+12223334444"
      val contactMethodType = UserIdentifierType.PHONE_NUMBER

      val service = DefaultPasswordService(databaseService, getTestEncoder(serviceKda), serviceKda, createTransactionalOperatorDouble())

      // Mock the data stored in the database
      val userPasswordRecord = createTestUserPassword(dbKda, RAW_PASSWORD)
      val now = Instant.now().toEpochMilli().toBigInteger()
      whenever(databaseService.findUserAccountByUserIdentifier(any())).thenReturn(
        UserAccount(USER_ACCOUNT_ID, now, now, 0.toBigInteger())
      )
      whenever(databaseService.findOneUserPasswordByUserAccountId(eq(USER_ACCOUNT_ID))).thenReturn(userPasswordRecord)

      // WHEN
      val correctPasswordAccepted = service.authenticateByContactMethod(
        contactMethod,
        contactMethodType,
        RAW_PASSWORD
      )
      val incorrectPasswordAccepted = service.authenticateByContactMethod(
        contactMethod,
        contactMethodType,
        RAW_PASSWORD.reversed()
      )

      // THEN
      Assertions.assertTrue(correctPasswordAccepted)
      Assertions.assertFalse(incorrectPasswordAccepted)
    }
  }

  private fun createTestUserPassword(kda: KeyDerivationAlgorithmType, rawPassword: String): UserPassword {
    val dbHash = getTestEncoder(kda).encode(rawPassword)
    val now = Instant.now().toEpochMilli().toBigInteger()

    return UserPassword(
      id = 1.toBigInteger(),
      USER_ACCOUNT_ID,
      KeyDerivationAlgorithmType.BCRYPT,
      dbHash,
      createdAt = now,
      lastModified = now,
      version = 1.toBigInteger(),
    )
  }
}
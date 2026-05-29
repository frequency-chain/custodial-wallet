package io.amplica.custodial_wallet.db.conf

import io.amplica.custodial_wallet.db.TestUserHelper
import io.amplica.custodial_wallet.db.repository.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

object BeanNames {
  const val TEST_USER_HELPER = "testUserHelper"
}

@Configuration
class DbTestConfig {
  @Bean
  fun testUserHelper(
    @Autowired reactiveUserAccountRepository: ReactiveUserAccountRepository,
    @Autowired reactiveUserKeyDataRepository: ReactiveUserKeyDataRepository,
    @Autowired reactiveProviderExternalUserRepository: ReactiveProviderExternalUserRepository,
    @Autowired reactiveUserIdentifierRepository: ReactiveUserIdentifierRepository,
    @Autowired reactiveUserAccountUserIdentifierRepository: ReactiveUserAccountUserIdentifierRepository,
    @Autowired reactiveProviderExternalUserDetailRepository: ReactiveProviderExternalUserDetailRepository,
  ): TestUserHelper {
    return TestUserHelper(
      reactiveUserAccountRepository,
      reactiveUserKeyDataRepository,
      reactiveProviderExternalUserRepository,
      reactiveUserIdentifierRepository,
      reactiveUserAccountUserIdentifierRepository,
      reactiveProviderExternalUserDetailRepository,
    )
  }
}
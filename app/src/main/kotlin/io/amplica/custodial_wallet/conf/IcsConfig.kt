package io.amplica.custodial_wallet.conf

import com.strategyobject.substrateclient.crypto.ss58.SS58AddressFormat
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.db.conf.DbBeanNames
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.orchestration.LookupOrchestrationService
import io.amplica.custodial_wallet.orchestration.ics.DefaultIcsUserOrchestrationProperties
import io.amplica.custodial_wallet.orchestration.ics.DefaultIcsUserOrchestrationService
import io.amplica.custodial_wallet.orchestration.ics.IcsUserOrchestrationService
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.orchestration.siwa.SiwaOrchestrationService
import io.amplica.custodial_wallet.service.ics.IcsService
import io.amplica.custodial_wallet.service.ics.JavaSdkIcsService
import io.amplica.custodial_wallet.service.ics_whitelist.IcsWhitelistService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.frequency.client.FrequencyClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class IcsConfig {
  @Bean
  fun defaultIcsUserOrchestrationProperties(
    @Value("\${${PropertyNames.SIGNUP_EXPIRATION_NUM_OF_BLOCKS}}") signupBlockExpiration: Long,
    @Value("\${unfinished.custodial-wallet.ics.storage-service.hostname}") storageServiceHostName: String,
    @Value("\${unfinished.custodial-wallet.ics.can.return.existing.context.item.key}") canReturnExistingContextItemKey: Boolean,
  ): DefaultIcsUserOrchestrationProperties {
    return DefaultIcsUserOrchestrationProperties(
      signupBlockExpiration,
      storageServiceHostName,
      canReturnExistingContextItemKey
    )
  }

  @Bean
  fun icsKeyService(
    @Qualifier(BeanNames.FREQUENCY_CLIENT) frequencyClient: FrequencyClient,
  ): IcsService {
    return JavaSdkIcsService(frequencyClient)
  }

  @Bean
  fun icsUserOrchestrationService(
    @Qualifier(BeanNames.DEFAULT_ICS_USER_ORCHESTRATION_PROPERTIES) defaultIcsUserOrchestrationProperties: DefaultIcsUserOrchestrationProperties,
    @Qualifier(BeanNames.LOOKUP_ORCHESTRATION_SERVICE) lookupOrchestrationService: LookupOrchestrationService,
    @Qualifier(BeanNames.SIGNING_ORCHESTRATION_SERVICE) signingOrchestrationService: SigningOrchestrationService,
    @Qualifier(BeanNames.KEY_SERVICE) keyService: KeyService,
    @Qualifier(BeanNames.ICS_KEY_SERVICE) icsService: IcsService,
    @Qualifier(BeanNames.SIWA_ORCHESTRATION_SERVICE) siwaOrchestrationService: SiwaOrchestrationService,
    @Qualifier(BeanNames.SS_58_ADDRESS_FORMAT) ss58AddressFormat: SS58AddressFormat,
    @Qualifier(DbBeanNames.DELEGATING_TRANSACTIONAL_OPERATOR) delegatingTransactionalOperator: DelegatingTransactionalOperator,
    @Qualifier(BeanNames.ICS_WHITELIST_SERVICE) icsWhitelistService: IcsWhitelistService,
    @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) databaseService: CustodialWalletDatabaseService,
  ): IcsUserOrchestrationService {
    return DefaultIcsUserOrchestrationService(
      defaultIcsUserOrchestrationProperties,
      lookupOrchestrationService,
      signingOrchestrationService,
      keyService,
      icsService,
      ss58AddressFormat,
      icsWhitelistService,
      databaseService,
      delegatingTransactionalOperator,
    )
  }
}

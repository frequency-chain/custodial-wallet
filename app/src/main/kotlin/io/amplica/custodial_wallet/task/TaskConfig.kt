package io.amplica.custodial_wallet.task

import com.strategyobject.substrateclient.crypto.KeyPair
import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.client.redis.CustodialWalletRedisClient
import io.amplica.custodial_wallet.conf.BeanNames
import io.amplica.custodial_wallet.db.conf.DbBeanNames
import io.amplica.custodial_wallet.db.repository.KeyUsageType
import io.amplica.custodial_wallet.orchestration.signing.SigningOrchestrationService
import io.amplica.custodial_wallet.service.key.KeyService
import io.amplica.custodial_wallet.util.key_creation.KeyPairType
import io.amplica.frequency.client.FrequencyClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigInteger

@Configuration
class TaskConfig {

    object TaskBeanNames {
        const val MIGRATE_KEYS_PROPERTIES = "migrateKeysProperties"
    }

    // TODO create values for migrateKeysProperties in properties files
    @Bean
    fun migrateKeysProperties(
        @Value("\${unfinished.custodial-wallet.migrate-keys.enabled}") migrateKeysEnabled: Boolean,
        @Value("\${unfinished.custodial-wallet.migrate-keys.existing-key-pair-type}") existingKeyPairType: KeyPairType,
        @Value("\${unfinished.custodial-wallet.migrate-keys.key-usage-type}") keyUsageType: KeyUsageType,
        @Value("\${unfinished.custodial-wallet.migrate-keys.missing-key-pair-type}") missingKeyPairType: KeyPairType,
        @Value("\${unfinished.custodial-wallet.migrate-keys.keys-per-batch}") keysPerBatch: Int,
        @Value("\${unfinished.custodial-wallet.migrate-keys.capacity-cost-per-key}") capacityCostPerKey: BigInteger,
        @Value("\${unfinished.custodial-wallet.migrate-keys.block-expiration}") blockExpiration: Long,
        ): MigrateKeysProperties {
        return MigrateKeysProperties(
            migrateKeysEnabled,
            existingKeyPairType,
            keyUsageType,
            missingKeyPairType,
            keysPerBatch,
            capacityCostPerKey,
            blockExpiration,
        )
    }

    @Bean
    fun migrateKeysTask(
        @Qualifier(TaskBeanNames.MIGRATE_KEYS_PROPERTIES) properties: MigrateKeysProperties,
        @Qualifier(DbBeanNames.CUSTODIAL_WALLET_DATABASE_SERVICE) databaseService: CustodialWalletDatabaseService,
        @Qualifier(BeanNames.FREQUENCY_CLIENT) frequencyClient: FrequencyClient,
        @Qualifier(BeanNames.SIGNING_ORCHESTRATION_SERVICE) signingOrchestrationService: SigningOrchestrationService,
        @Qualifier(BeanNames.REDIS_CLIENT) redisClient: CustodialWalletRedisClient,
        @Qualifier(BeanNames.KEY_SERVICE) keyService: KeyService,
        @Qualifier("keyPair") keyPair: KeyPair,
    ): MigrateKeysTask {

        return MigrateKeysTask(
            properties,
            databaseService,
            frequencyClient,
            signingOrchestrationService,
            redisClient,
            keyService,
            keyPair
        )
    }
}

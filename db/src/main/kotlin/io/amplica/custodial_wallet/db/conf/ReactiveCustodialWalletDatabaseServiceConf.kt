package io.amplica.custodial_wallet.db.conf

import io.amplica.custodial_wallet.CustodialWalletDatabaseService
import io.amplica.custodial_wallet.OrganizationDatabaseService
import io.amplica.custodial_wallet.ReactiveCustodialWalletDatabaseService
import io.amplica.custodial_wallet.ReactiveOrganizationDatabaseService
import io.amplica.custodial_wallet.db.TimingCustodialWalletDatabaseService
import io.amplica.custodial_wallet.db.repository.*
import io.amplica.custodial_wallet.db.repository.organization.ReactiveOrganizationAssetRepository
import io.amplica.custodial_wallet.db.repository.organization.ReactiveOrganizationRepository
import io.amplica.custodial_wallet.db.repository.organization.ReactiveProviderFrequencyAccountRepository
import io.amplica.custodial_wallet.db.repository.organization.ReactiveWhitelistedOriginDescriptorRepository
import io.amplica.custodial_wallet.db.repository.organization.application.ReactiveProviderApplicationAssetRepository
import io.amplica.custodial_wallet.db.repository.organization.application.ReactiveProviderApplicationRepository
import io.amplica.custodial_wallet.db.repository.organization.application.ReactiveProviderApplicationWhitelistedOriginDescriptorRepository
import io.amplica.custodial_wallet.db.spring.ConnectionMode
import io.amplica.custodial_wallet.db.spring.DelegatingTransactionalOperator
import io.amplica.custodial_wallet.db.spring.RoutingConnectionFactory
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.postgresql.MultiHostConnectionStrategy.TargetServerType
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.postgresql.client.SSLMode
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.Option
import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties.Pool
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.PropertyMapper
import org.springframework.boot.r2dbc.OptionsCapableConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.util.Assert
import java.time.Duration

object DbBeanNames {
  const val CUSTODIAL_WALLET_DATABASE_SERVICE = "custodialWalletDatabaseService"
  const val ORGANIZATION_DATABASE_SERVICE = "organizationDatabaseService"
  const val READ_ONLY_TRANSACTIONAL_OPERATOR = "readOnlyTransactionalOperator"
  const val READ_WRITE_TRANSACTIONAL_OPERATOR = "readWriteTransactionalOperator"
  const val DELEGATING_TRANSACTIONAL_OPERATOR = "delegatingTransactionalOperator"
}

@Configuration
@EnableR2dbcRepositories
@EnableConfigurationProperties(R2dbcProperties::class)
class ReactiveCustodialWalletDatabaseServiceConf {
  fun createPooledPostgresConnectionFactory(
    username: String,
    password: String,
    url: String,
    targetServerType: TargetServerType,
    poolProperties: Pool,
    statementTimeout: Duration,
  ): ConnectionFactory {
    val isR2dbc = url.startsWith("r2dbc", true)
    Assert.isTrue(isR2dbc, "The url doesn't seem to be r2dbc enabled $url")

    val connectionFactoryOptions = ConnectionFactoryOptions.parse(url)

    val driver = connectionFactoryOptions.getRequiredValue(ConnectionFactoryOptions.DRIVER) as String
    val isPostgres = driver.equals("postgresql", true)
    Assert.isTrue(isPostgres, "$driver is not the expected 'postgresql'")

    val postgresqlConnectionConfiguration = PostgresqlConnectionConfiguration.builder()
      .host(connectionFactoryOptions.getRequiredValue(ConnectionFactoryOptions.HOST) as String)
      .port(connectionFactoryOptions.getRequiredValue(ConnectionFactoryOptions.PORT) as Int)
      .database(connectionFactoryOptions.getRequiredValue(ConnectionFactoryOptions.DATABASE) as String)
      .username(username)
      .password(password)
      .schema(connectionFactoryOptions.getRequiredValue(Option.valueOf<String>("schema")) as String)
      .statementTimeout(statementTimeout)
      .sslMode(SSLMode.ALLOW)
      .build()

    val connectionFactory = PostgresqlConnectionFactory(postgresqlConnectionConfiguration)

    val map = PropertyMapper.get().alwaysApplyingWhenNonNull()
    val builder = ConnectionPoolConfiguration.builder(connectionFactory)
    map.from(poolProperties.maxIdleTime).to { builder.maxIdleTime(it) }
    map.from(poolProperties.maxLifeTime).to { builder.maxLifeTime(it) }
    map.from(poolProperties.maxAcquireTime).to { builder.maxAcquireTime(it) }
    map.from(poolProperties.maxCreateConnectionTime).to { builder.maxCreateConnectionTime(it) }
    map.from(poolProperties.initialSize).to { builder.initialSize(it) }
    map.from(poolProperties.maxSize).to { builder.maxSize(it) }
    map.from(poolProperties.validationQuery).whenHasText().to { builder.validationQuery(it) }
    map.from(poolProperties.validationDepth).to { builder.validationDepth(it) }
    map.from(poolProperties.minIdle).to { builder.minIdle(it) }
    map.from(poolProperties.maxValidationTime).to { builder.maxValidationTime(it) }

    return ConnectionPool(builder.build())
  }

  fun createReadWriteConnectionFactory(r2dbcProperties: R2dbcProperties, statementTimeout: Duration): ConnectionFactory {
    return createPooledPostgresConnectionFactory(
      r2dbcProperties.username,
      r2dbcProperties.password,
      r2dbcProperties.url,
      TargetServerType.PRIMARY,
      r2dbcProperties.pool,
      statementTimeout,
    )
  }

  fun createReadOnlyConnectionFactory(
    r2dbcProperties: R2dbcProperties,
    readOnlyR2dbcUrl: String,
    statementTimeout: Duration
  ): ConnectionFactory {
    return createPooledPostgresConnectionFactory(
      r2dbcProperties.username,
      r2dbcProperties.password,
      readOnlyR2dbcUrl,
      TargetServerType.SECONDARY,
      r2dbcProperties.pool,
      statementTimeout
    )
  }

  @Bean
  fun routingConnectionFactory(
    r2dbcProperties: R2dbcProperties,
    @Value("\${unfinished.custodial-wallet.ro.r2dbc.url}") readOnlyR2dbcUrl: String,
    @Value("\${unfinished.custodial-wallet.r2dbc.statement.timeout.millis}") statementTimeout: Long
  ): ConnectionFactory {
    //So even though this is used for the read write node it seems the properties is only used to detect if it's an embedded
    //database so the underlying
    val connectionFactoryOptions = ConnectionFactoryOptions.parse(r2dbcProperties.url)

    val statementTimeoutDuration = Duration.ofMillis(statementTimeout)
    val pooledReadWritePostgresqlConnectionFactory = createReadWriteConnectionFactory(r2dbcProperties, statementTimeoutDuration)
    val pooledReadOnlyPostgresqlConnectionFactory = createReadOnlyConnectionFactory(r2dbcProperties, readOnlyR2dbcUrl, statementTimeoutDuration)

    val routingConnectionFactory = RoutingConnectionFactory()

    val connectionModeToConnectionFactory = mapOf(
      ConnectionMode.READ_ONLY to pooledReadOnlyPostgresqlConnectionFactory,
      ConnectionMode.READ_WRITE to pooledReadWritePostgresqlConnectionFactory
    )
    routingConnectionFactory.setTargetConnectionFactories(connectionModeToConnectionFactory)
    routingConnectionFactory.setDefaultTargetConnectionFactory(pooledReadWritePostgresqlConnectionFactory)

    routingConnectionFactory.initialize()

    return OptionsCapableConnectionFactory(connectionFactoryOptions, routingConnectionFactory)
  }

  @Bean
  fun readOnlyTransactionalOperator(reactiveTransactionManager: ReactiveTransactionManager): TransactionalOperator {
    val definition = DefaultTransactionDefinition()
    definition.isReadOnly = true
    definition.timeout = 5 //seconds
    return TransactionalOperator.create(reactiveTransactionManager, definition)
  }

  @Bean
  fun readWriteTransactionalOperator(reactiveTransactionManager: ReactiveTransactionManager): TransactionalOperator {
    val definition = DefaultTransactionDefinition()
    definition.isReadOnly = false
    definition.timeout = 5 //seconds
    return TransactionalOperator.create(reactiveTransactionManager)
  }

  @Bean
  fun delegatingTransactionalOperator(
    @Qualifier(DbBeanNames.READ_ONLY_TRANSACTIONAL_OPERATOR) readOnlyTransactionalOperator: TransactionalOperator,
    @Qualifier(DbBeanNames.READ_WRITE_TRANSACTIONAL_OPERATOR) readWriteTransactionalOperator: TransactionalOperator
  ): DelegatingTransactionalOperator {
    return DelegatingTransactionalOperator(readWriteTransactionalOperator, readOnlyTransactionalOperator)
  }

  @Bean
  fun custodialWalletDatabaseService(
    reactiveUserAccountRepository: ReactiveUserAccountRepository,
    reactiveUserKeyDataRepository: ReactiveUserKeyDataRepository,
    reactiveProviderExternalUserRepository: ReactiveProviderExternalUserRepository,
    reactiveProviderExternalUserDetailRepository: ReactiveProviderExternalUserDetailRepository,
    reactiveUserIdentifierRepository: ReactiveUserIdentifierRepository,
    reactiveUserAccountUserIdentifierRepository: ReactiveUserAccountUserIdentifierRepository,
    reactiveAuditSessionRecordRepository: ReactiveAuditSessionRecordRepository,
    reactiveUserPasswordRepository: ReactiveUserPasswordRepository,
    reactiveWalletRepository: ReactiveWalletRepository,
    reactiveCredentialRepository: ReactiveCredentialRepository,
    reactiveCredentialTransportRepository: ReactiveCredentialTransportRepository,
    reactiveOptInRepository: ReactiveOptInRepository,
    reactiveUserSeedDataRepository: ReactiveUserSeedDataRepository,
    reactiveUserDerivedKeyDataRepository: ReactiveUserDerivedKeyDataRepository,
    @Qualifier(DbBeanNames.DELEGATING_TRANSACTIONAL_OPERATOR) delegatingTransactionalOperator: DelegatingTransactionalOperator,
    reactiveWalletMetadataRepository: ReactiveWalletMetadataRepository,
  ): CustodialWalletDatabaseService {
    return TimingCustodialWalletDatabaseService(
      ReactiveCustodialWalletDatabaseService(
        reactiveUserAccountRepository,
        reactiveUserKeyDataRepository,
        reactiveProviderExternalUserRepository,
        reactiveProviderExternalUserDetailRepository,
        reactiveUserIdentifierRepository,
        reactiveUserAccountUserIdentifierRepository,
        reactiveAuditSessionRecordRepository,
        reactiveUserPasswordRepository,
        reactiveWalletRepository,
        reactiveCredentialRepository,
        reactiveCredentialTransportRepository,
        reactiveWalletMetadataRepository,
        reactiveOptInRepository,
        reactiveUserSeedDataRepository,
        reactiveUserDerivedKeyDataRepository,
        delegatingTransactionalOperator,
      )
    )
  }

  @Bean
  fun organizationDatabaseService(
    reactiveOrganizationRepository: ReactiveOrganizationRepository,
    reactiveProviderFrequencyAccountRepository: ReactiveProviderFrequencyAccountRepository,
    reactiveWhitelistedOriginDescriptorRepository: ReactiveWhitelistedOriginDescriptorRepository,
    reactiveOrganizationAssetRepository: ReactiveOrganizationAssetRepository,
    reactiveProviderApplicationRepository: ReactiveProviderApplicationRepository,
    reactiveProviderApplicationWhitelistedOriginDescriptorRepository: ReactiveProviderApplicationWhitelistedOriginDescriptorRepository,
    reactiveProviderApplicationAssetRepository: ReactiveProviderApplicationAssetRepository,
    @Qualifier(DbBeanNames.DELEGATING_TRANSACTIONAL_OPERATOR) delegatingTransactionalOperator: DelegatingTransactionalOperator,
  ): OrganizationDatabaseService {
    return ReactiveOrganizationDatabaseService(
      reactiveOrganizationRepository,
      reactiveProviderFrequencyAccountRepository,
      reactiveWhitelistedOriginDescriptorRepository,
      reactiveOrganizationAssetRepository,
      reactiveProviderApplicationRepository,
      reactiveProviderApplicationWhitelistedOriginDescriptorRepository,
      reactiveProviderApplicationAssetRepository,
      delegatingTransactionalOperator
    )
  }

  @Bean
  fun reactiveProviderExternalUserDaoImpl(databaseClient: DatabaseClient): ReactiveProviderExternalUserDaoImpl {
    return ReactiveProviderExternalUserDaoImpl(databaseClient)
  }

  @Bean
  fun reactiveUserAccountRepositoryDaoImpl(databaseClient: DatabaseClient): ReactiveUserAccountRepositoryDaoImpl {
    return ReactiveUserAccountRepositoryDaoImpl(databaseClient)
  }

  @Bean
  fun flyway(
    r2dbcProperties: R2dbcProperties,
    @Value("\${unfinished.custodial-wallet.db.service.createSchema}") createSchema: Boolean,
    @Value("\${unfinished.custodial-wallet.db.service.schema}") schema: String? = null
  ): Flyway {
    val jdbcUrl = r2dbcProperties.url.replace("r2dbc", "jdbc")
    val flyway = Flyway.configure().dataSource(jdbcUrl, r2dbcProperties.username, r2dbcProperties.password)
      .locations("classpath:migrations/postgres")
      .createSchemas(createSchema)
      .schemas(schema)
      .load()
    flyway.migrate()

    return flyway
  }
}
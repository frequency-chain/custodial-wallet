package io.amplica.custodial_wallet.container

import org.testcontainers.containers.PostgreSQLContainer

class PostgresTestContainer : PostgreSQLContainer<Nothing>("postgres:17.7"), CustodialWalletTestContainer {
  companion object {
    const val USERNAME = "unfinished"
    const val PASSWORD = "somePassword"
    const val DATABASE_NAME = "custodial_wallet_dev"
    const val SCHEMA = "custodial_wallet"
  }

  init {
    withUsername(USERNAME)
    withPassword(PASSWORD)
    withDatabaseName(DATABASE_NAME)
  }

  fun getUrl() = "r2dbc:postgresql://${host}:${getMappedPort(5432)}/${DATABASE_NAME}?schema=${SCHEMA}"

  override fun getPropertyValues(): Map<String, String> {
    return mapOf(
      "spring.r2dbc.url" to getUrl(),
      "unfinished.custodial-wallet.ro.r2dbc.url" to getUrl(),
      "spring.r2dbc.username" to USERNAME,
      "spring.r2dbc.password" to PASSWORD,
    )
  }
}

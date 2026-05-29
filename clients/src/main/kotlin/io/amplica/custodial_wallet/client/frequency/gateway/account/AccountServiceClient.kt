package io.amplica.custodial_wallet.client.frequency.gateway.account

interface AccountServiceClient {
  suspend fun healthcheck(): Boolean
}
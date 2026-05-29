package io.amplica.custodial_wallet.container

object FrequencyGatewayContainerProperties {

  object Common {
    fun getEnv(frequencyAddress: String, redisUrl: String) = mapOf(
      "CAPACITY_LIMIT" to """{"type":"percentage", "value":80}""",
      "CHAIN_ENVIRONMENT" to "dev",
      "DEBUG" to "true",
      "FREQUENCY_API_WS_URL" to frequencyAddress,
      "HEALTH_CHECK_MAX_RETRIES" to "4",
      "HEALTH_CHECK_MAX_RETRY_INTERVAL_SECONDS" to "10",
      "HEALTH_CHECK_SUCCESS_THRESHOLD" to "10",
      "IPFS_BASIC_AUTH_SECRET" to "",
      "IPFS_BASIC_AUTH_USER" to "",
      "IPFS_ENDPOINT" to "http://ipfs:5001",
      "IPFS_GATEWAY_URL" to "https://ipfs.io/ipfs/[CID]",
      "PROVIDER_ACCOUNT_SEED_PHRASE" to "//Alice",
      "PROVIDER_ID" to "1",
      "QUEUE_HIGH_WATER" to "1000",
      "REDIS_URL" to redisUrl,
      "SIWF_DOMAIN" to "localhost",
      "SIWF_NODE_RPC_URL" to "http://localhost:9944",
      "SIWF_URL" to "https://projectlibertylabs.github.io/siwf/v1/ui",
      "WEBHOOK_FAILURE_THRESHOLD" to "3",
      "WEBHOOK_RETRY_INTERVAL_SECONDS" to "10",
    )
  }

  object AccountService {
    const val VERSION = "1.4.0"
    const val IMAGE = "projectlibertylabs/account-service"

    val ENV = mapOf(
      "BLOCKCHAIN_SCAN_INTERVAL_SECONDS" to "1",
      "CACHE_KEY_PREFIX" to "account-service:",
      "GRAPH_ENVIRONMENT_TYPE" to "Mainnet",
      "SIWF_V2_URI_VALIDATION" to "localhost",
      "TRUST_UNFINALIZED_BLOCKS" to "true",
      // TODO: Figure out how the heck this is gonna work
      "WEBHOOK_BASE_URL" to "http://mock-webhook-logger:3001/webhooks/account-service",
    )
  }

}
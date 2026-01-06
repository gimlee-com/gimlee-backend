package com.gimlee.payments.crypto.piratechain.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gimlee.payments.pirate-chain.client")
data class PirateChainClientProperties(
    val rpcUrl: String,
    val user: String,
    val password: String,
    val maxConnections: Int,
    val connectionRequestTimeoutMillis: Long,
    val responseTimeoutMillis: Long,
)
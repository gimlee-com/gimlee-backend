package com.gimlee.payments.crypto.ycash.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gimlee.payments.ycash.client")
data class YcashClientProperties(
    val rpcUrl: String,
    val user: String,
    val password: String,
    val maxConnections: Int,
    val connectionRequestTimeoutMillis: Long,
    val responseTimeoutMillis: Long,
)

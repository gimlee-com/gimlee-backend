package com.gimlee.payments.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "gimlee.payments")
data class PaymentProperties(
    @DefaultValue("1")
    val timeoutHours: Long,
    val pirateChain: PirateChainProperties = PirateChainProperties()
)

data class PirateChainProperties(
    @DefaultValue("gimlee:")
    val memoPrefix: String = "gimlee:",
    @DefaultValue("60000")
    val monitorDelayMs: Long = 60000,
    @DefaultValue("1")
    val minConfirmations: Int = 1
)

package com.gimlee.payments.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "gimlee.payments")
data class PaymentProperties(
    @DefaultValue("1")
    val timeoutHours: Long,
    val pirateChain: PirateChainProperties = PirateChainProperties(),
    val ycash: YcashProperties = YcashProperties(),
    val exchange: ExchangeProperties = ExchangeProperties()
)

data class PirateChainProperties(
    @DefaultValue("gimlee:")
    val memoPrefix: String = "gimlee:",
    @DefaultValue("10000")
    val monitorDelayMs: Long = 10000,
    @DefaultValue("1")
    val minConfirmations: Int = 1,
    @DefaultValue("5")
    val monitorThreads: Int = 5
)

data class YcashProperties(
    @DefaultValue("gimlee:")
    val memoPrefix: String = "gimlee:",
    @DefaultValue("10000")
    val monitorDelayMs: Long = 10000,
    @DefaultValue("1")
    val minConfirmations: Int = 1,
    @DefaultValue("1")
    val monitorThreads: Int = 1
)

data class ExchangeProperties(
    val bitmart: BitmartProperties = BitmartProperties(),
    @DefaultValue("60000")
    val updateIntervalMs: Long = 60000
)

data class BitmartProperties(
    @DefaultValue("https://api-cloud.bitmart.com")
    val baseUrl: String = "https://api-cloud.bitmart.com",
    @DefaultValue("0.05")
    val volatilityThreshold: Double = 0.05,
    @DefaultValue("10")
    val volatilityKlinesLimit: Int = 10
)

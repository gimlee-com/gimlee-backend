package com.gimlee.payments.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties(prefix = "gimlee.payments")
data class PaymentProperties @ConstructorBinding constructor(
    @DefaultValue("1")
    val timeoutHours: Long,
    val pirateChain: PirateChainProperties = PirateChainProperties(),
    val ycash: YcashProperties = YcashProperties(),
    val exchange: ExchangeProperties = ExchangeProperties(),
    val volatility: VolatilityProperties
)

data class VolatilityProperties(
    val downsideThreshold: Double,
    val windowSeconds: Long,
    val cooldownSeconds: Long,
    val stabilizationChecks: Int,
    val staleThresholdSeconds: Long
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
    val mexc: MexcProperties = MexcProperties(),
    val openExchangeRates: OpenExchangeRatesProperties = OpenExchangeRatesProperties(),
    @DefaultValue("60000")
    val updateIntervalMs: Long = 60000,
    val cache: CacheProperties = CacheProperties()
)

data class CacheProperties(
    @DefaultValue("60")
    val expireAfterWriteSeconds: Long = 60
)

data class BitmartProperties(
    @DefaultValue("https://api-cloud.bitmart.com")
    val baseUrl: String = "https://api-cloud.bitmart.com",
    @DefaultValue("0.05")
    val volatilityThreshold: Double = 0.05,
    @DefaultValue("10")
    val volatilityKlinesLimit: Int = 10
)

data class MexcProperties(
    @DefaultValue("https://api.mexc.com")
    val baseUrl: String = "https://api.mexc.com",
    @DefaultValue("0.05")
    val volatilityThreshold: Double = 0.05,
    @DefaultValue("10")
    val volatilityKlinesLimit: Int = 10,
    @DefaultValue("60m")
    val volatilityKlinesInterval: String = "60m"
)

data class OpenExchangeRatesProperties(
    @DefaultValue("https://openexchangerates.org/api")
    val baseUrl: String = "https://openexchangerates.org/api",
    val appId: String? = null,
    @DefaultValue("21600000") // 6 hours
    val updateIntervalMs: Long = 21600000
)

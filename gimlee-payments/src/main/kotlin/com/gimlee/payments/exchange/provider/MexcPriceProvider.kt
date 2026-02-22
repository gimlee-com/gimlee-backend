package com.gimlee.payments.exchange.provider

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.exchange.config.ExchangeConfig
import com.gimlee.payments.exchange.domain.ExchangePriceProvider
import com.gimlee.payments.exchange.domain.ExchangePriceResult
import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Component
@Order(20)
class MexcPriceProvider(
    @Qualifier(ExchangeConfig.EXCHANGE_HTTP_CLIENT) private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val paymentProperties: PaymentProperties
) : ExchangePriceProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "Mexc"

    private val supportedPairs = mapOf(
        (Currency.ARRR to Currency.USDT) to "ARRRUSDT"
    )

    override fun supports(base: Currency, quote: Currency): Boolean {
        return supportedPairs.containsKey(base to quote)
    }

    override fun fetchPrice(base: Currency, quote: Currency): ExchangePriceResult? {
        val symbol = supportedPairs[base to quote] ?: return null
        val mexcProps = paymentProperties.exchange.mexc
        val limit = mexcProps.volatilityKlinesLimit
        val url = "${mexcProps.baseUrl}/api/v3/klines?symbol=$symbol&interval=${mexcProps.volatilityKlinesInterval}&limit=$limit"
        val request = HttpGet(url)

        return try {
            httpClient.execute(request) { response ->
                val statusCode = response.code
                val entity = response.entity
                val json = entity?.let { EntityUtils.toString(it) }

                if (statusCode in 200..299 && json != null) {
                    try {
                        // Response is List<List<Any>>
                        val klines: List<List<Any>> = objectMapper.readValue(json, object : TypeReference<List<List<Any>>>() {})
                        
                        if (klines.isNotEmpty()) {
                            // Get the latest kline
                            val latestKline = klines.last()
                            
                            // Format: [Open time, Open, High, Low, Close, Volume, Close time, ...]
                            // Index 4 is Close price.
                            val closePriceStr = latestKline[4].toString()
                            val price = BigDecimal(closePriceStr)
                            
                            // Timestamp is index 6 (Close time)
                            val timestampLong = (latestKline[6] as Number).toLong()
                            val timestamp = Instant.ofEpochMilli(timestampLong)

                            val isVolatile = checkVolatility(symbol, klines)

                            ExchangePriceResult(
                                rate = price,
                                timestamp = timestamp,
                                isVolatile = isVolatile
                            )
                        } else {
                            log.warn("Mexc returned empty klines for $symbol")
                            null
                        }
                    } catch (e: Exception) {
                        log.error("Failed to parse Mexc response for $symbol: ${e.message}. Response: $json")
                        null
                    }
                } else {
                    log.error("Mexc API HTTP error for $symbol: $statusCode. Response: $json")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Failed to fetch price from Mexc for $symbol: ${e.message}")
            null
        }
    }

    private fun checkVolatility(symbol: String, klines: List<List<Any>>): Boolean {
        if (klines.isEmpty()) return false

        var minLow: BigDecimal? = null
        var maxHigh: BigDecimal? = null

        for (kline in klines) {
            if (kline.size >= 5) {
                try {
                    // Index 2 is High, Index 3 is Low
                    val high = BigDecimal(kline[2].toString())
                    val low = BigDecimal(kline[3].toString())
                    
                    if (minLow == null || low < minLow) minLow = low
                    if (maxHigh == null || high > maxHigh) maxHigh = high
                } catch (e: Exception) {
                    log.warn("Failed to parse kline data for volatility check of $symbol: $kline")
                }
            }
        }

        if (minLow != null && maxHigh != null && minLow > BigDecimal.ZERO) {
            val swing = (maxHigh - minLow).divide(minLow, 4, RoundingMode.HALF_UP)
            val threshold = BigDecimal.valueOf(paymentProperties.exchange.mexc.volatilityThreshold)
            
            if (swing >= threshold) {
                log.info("Volatile price detected for $symbol: swing=${swing.multiply(BigDecimal("100"))}% (threshold=${threshold.multiply(BigDecimal("100"))}%)")
                return true
            }
        }
        return false
    }
}

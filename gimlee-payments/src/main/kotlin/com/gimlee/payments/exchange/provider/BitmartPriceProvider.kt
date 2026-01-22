package com.gimlee.payments.exchange.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.exchange.client.model.BitmartKlinesResponse
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
import java.time.Instant

@Component
@Order(10)
class BitmartPriceProvider(
    @Qualifier(ExchangeConfig.EXCHANGE_HTTP_CLIENT) private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val paymentProperties: PaymentProperties
) : ExchangePriceProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "Bitmart"

    private val supportedPairs = mapOf(
        (Currency.YEC to Currency.USDT) to "YEC_USDT"
    )

    override fun supports(base: Currency, quote: Currency): Boolean {
        return supportedPairs.containsKey(base to quote)
    }

    override fun fetchPrice(base: Currency, quote: Currency): ExchangePriceResult? {
        val symbol = supportedPairs[base to quote] ?: return null
        val bitmartProps = paymentProperties.exchange.bitmart
        val limit = bitmartProps.volatilityKlinesLimit
        val url = "${bitmartProps.baseUrl}/spot/quotation/v3/klines?symbol=$symbol&limit=$limit"
        val request = HttpGet(url)

        return try {
            httpClient.execute(request) { response ->
                val statusCode = response.code
                val entity = response.entity
                val json = entity?.let { EntityUtils.toString(it) }

                if (statusCode in 200..299 && json != null) {
                    val bitmartResponse = objectMapper.readValue(json, BitmartKlinesResponse::class.java)
                    if (bitmartResponse.code == 1000) {
                        val data = bitmartResponse.data ?: emptyList()
                        val firstKline = data.firstOrNull()
                        if (firstKline != null && firstKline.size >= 5) {
                            val timestampSeconds = firstKline[0].toLong()
                            val closePrice = firstKline[4].toBigDecimal()

                            val isVolatile = checkVolatility(symbol, data)

                            ExchangePriceResult(
                                rate = closePrice,
                                timestamp = Instant.ofEpochSecond(timestampSeconds),
                                isVolatile = isVolatile
                            )
                        } else {
                            log.warn("Bitmart returned empty data or invalid format for $symbol")
                            null
                        }
                    } else {
                        log.error("Bitmart API error for $symbol: ${bitmartResponse.message} (code: ${bitmartResponse.code})")
                        null
                    }
                } else {
                    log.error("Bitmart API HTTP error for $symbol: $statusCode. Response: $json")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Failed to fetch price from Bitmart for $symbol: ${e.message}")
            null
        }
    }

    private fun checkVolatility(symbol: String, data: List<List<String>>): Boolean {
        if (data.isEmpty()) return false

        var minLow: BigDecimal? = null
        var maxHigh: BigDecimal? = null

        for (kline in data) {
            if (kline.size >= 4) {
                try {
                    val high = kline[2].toBigDecimal()
                    val low = kline[3].toBigDecimal()
                    if (minLow == null || low < minLow) minLow = low
                    if (maxHigh == null || high > maxHigh) maxHigh = high
                } catch (e: Exception) {
                    log.warn("Failed to parse kline data for volatility check of $symbol: $kline")
                }
            }
        }

        if (minLow != null && maxHigh != null && minLow > BigDecimal.ZERO) {
            val swing = (maxHigh - minLow).divide(minLow, 4, java.math.RoundingMode.HALF_UP)
            val threshold = BigDecimal.valueOf(paymentProperties.exchange.bitmart.volatilityThreshold)
            if (swing >= threshold) {
                log.info("Volatile price detected for $symbol: swing=${swing.multiply(BigDecimal("100"))}% (threshold=${threshold.multiply(BigDecimal("100"))}%)")
                return true
            }
        }
        return false
    }
}

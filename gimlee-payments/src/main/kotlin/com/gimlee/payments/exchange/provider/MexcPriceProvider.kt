package com.gimlee.payments.exchange.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.exchange.client.model.MexcPriceResponse
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
        val url = "${mexcProps.baseUrl}/api/v3/ticker/price?symbol=$symbol"
        val request = HttpGet(url)

        return try {
            httpClient.execute(request) { response ->
                val statusCode = response.code
                val entity = response.entity
                val json = entity?.let { EntityUtils.toString(it) }

                if (statusCode in 200..299 && json != null) {
                    try {
                        val mexcResponse = objectMapper.readValue(json, MexcPriceResponse::class.java)
                        val price = BigDecimal(mexcResponse.price)
                        ExchangePriceResult(
                            rate = price,
                            timestamp = Instant.now(), // MEXC ticker/price doesn't provide timestamp, assume current
                            isVolatile = false // Simple price check doesn't support volatility
                        )
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
}

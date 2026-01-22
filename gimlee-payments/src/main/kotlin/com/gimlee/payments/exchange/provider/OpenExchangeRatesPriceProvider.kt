package com.gimlee.payments.exchange.provider

import com.fasterxml.jackson.databind.ObjectMapper
import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.config.PaymentProperties
import com.gimlee.payments.exchange.client.model.OpenExchangeRatesResponse
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
@Order(10)
class OpenExchangeRatesPriceProvider(
    @Qualifier(ExchangeConfig.EXCHANGE_HTTP_CLIENT) private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper,
    private val paymentProperties: PaymentProperties
) : ExchangePriceProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "OpenExchangeRates"

    private var cachedResponse: OpenExchangeRatesResponse? = null
    private var lastFetchTime: Instant = Instant.MIN

    override fun supports(base: Currency, quote: Currency): Boolean {
        if (paymentProperties.exchange.openExchangeRates.appId.isNullOrBlank()) {
            return false
        }
        // We support anything relative to USD that OER provides.
        // For now, we specifically need USD/PLN and USD/XAU.
        // Also potentially PLN/USD or XAU/USD (via inverse).
        return (base == Currency.USD && (quote == Currency.PLN || quote == Currency.XAU)) ||
               (quote == Currency.USD && (base == Currency.PLN || base == Currency.XAU))
    }

    override fun fetchPrice(base: Currency, quote: Currency): ExchangePriceResult? {
        val response = getOrFetchResponse() ?: return null

        val usdBaseRate = response.rates[base.name]
        val usdQuoteRate = response.rates[quote.name]

        if (usdBaseRate == null || usdQuoteRate == null) {
            log.warn("OpenExchangeRates does not have rate for $base or $quote")
            return null
        }

        // Rate is units of quote per 1 unit of base.
        // OER gives units of X per 1 USD.
        // So 1 USD = usdQuoteRate quote
        // 1 USD = usdBaseRate base => 1 base = (1 / usdBaseRate) USD
        // 1 base = (1 / usdBaseRate) * usdQuoteRate quote = (usdQuoteRate / usdBaseRate) quote
        
        val rate = usdQuoteRate.divide(usdBaseRate, 18, RoundingMode.HALF_UP)

        return ExchangePriceResult(
            rate = rate,
            timestamp = Instant.ofEpochSecond(response.timestamp)
        )
    }

    @Synchronized
    private fun getOrFetchResponse(): OpenExchangeRatesResponse? {
        val now = Instant.now()
        val intervalMs = paymentProperties.exchange.openExchangeRates.updateIntervalMs
        
        if (cachedResponse != null && lastFetchTime.plusMillis(intervalMs).isAfter(now)) {
            return cachedResponse
        }

        val appId = paymentProperties.exchange.openExchangeRates.appId
        if (appId.isNullOrBlank()) {
            log.warn("OpenExchangeRates App ID is not configured. Skipping fetch.")
            return null
        }

        val baseUrl = paymentProperties.exchange.openExchangeRates.baseUrl
        val url = "$baseUrl/latest.json?app_id=$appId"
        val request = HttpGet(url)

        return try {
            log.info("Fetching latest rates from OpenExchangeRates...")
            httpClient.execute(request) { response ->
                val statusCode = response.code
                val entity = response.entity
                val json = entity?.let { EntityUtils.toString(it) }

                if (statusCode in 200..299 && json != null) {
                    val oerResponse = objectMapper.readValue(json, OpenExchangeRatesResponse::class.java)
                    cachedResponse = oerResponse
                    lastFetchTime = now
                    oerResponse
                } else {
                    log.error("OpenExchangeRates API error: $statusCode. Response: $json")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Failed to fetch rates from OpenExchangeRates: ${e.message}")
            null
        }
    }
}

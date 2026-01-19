package com.gimlee.payments.exchange.domain

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.domain.model.ExchangeRate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ExchangeRateFetcher(
    private val providers: List<ExchangePriceProvider>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Registry of which pairs to fetch
    private val pairsToFetch = listOf(
        Currency.YEC to Currency.USDT,
        Currency.ARRR to Currency.USDT,
        Currency.USDT to Currency.USD,
        Currency.USD to Currency.PLN
    )

    fun fetchAllLatestRates(): List<ExchangeRate> {
        val rates = mutableListOf<ExchangeRate>()

        for ((base, quote) in pairsToFetch) {
            val provider = providers.find { it.supports(base, quote) }
            if (provider != null) {
                try {
                    val result = provider.fetchPrice(base, quote)
                    if (result != null) {
                        rates.add(
                            ExchangeRate(
                                baseCurrency = base,
                                quoteCurrency = quote,
                                rate = result.rate,
                                updatedAt = result.timestamp ?: Instant.now(),
                                source = provider.name,
                                isVolatile = result.isVolatile
                            )
                        )
                    } else {
                        log.warn("Provider ${provider.name} returned null price for $base/$quote")
                    }
                } catch (e: Exception) {
                    log.error("Error fetching price from ${provider.name} for $base/$quote: ${e.message}")
                }
            } else {
                log.warn("No provider found for pair $base/$quote")
            }
        }

        return rates
    }
}

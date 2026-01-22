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
        Currency.USD to Currency.PLN,
        Currency.USD to Currency.XAU
    )

    /**
     * Fetches the latest exchange rates for all registered currency pairs.
     *
     * The method iterates through each pair defined in [pairsToFetch] and attempts to retrieve
     * a rate from the available [providers]. If multiple providers support the same pair,
     * the one with the lowest @Order (highest precedence) is tried first.
     *
     * If a high-priority provider fails or is not configured (returns null), the system
     * automatically falls back to the next available provider in order of precedence.
     *
     * @return A list of successfully fetched [ExchangeRate]s.
     */
    fun fetchAllLatestRates(): List<ExchangeRate> {
        val rates = mutableListOf<ExchangeRate>()

        for ((base, quote) in pairsToFetch) {
            val supportedProviders = providers.filter { it.supports(base, quote) }
            var rateFetched = false

            for (provider in supportedProviders) {
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
                        rateFetched = true
                        break // Success, move to next pair
                    } else {
                        log.warn("Provider ${provider.name} returned null price for $base/$quote")
                    }
                } catch (e: Exception) {
                    log.error("Error fetching price from ${provider.name} for $base/$quote: ${e.message}")
                }
            }

            if (!rateFetched) {
                log.warn("No provider succeeded in fetching price for pair $base/$quote")
            }
        }

        return rates
    }
}

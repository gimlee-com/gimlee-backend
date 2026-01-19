package com.gimlee.payments.exchange.domain

import com.gimlee.common.domain.model.Currency
import java.math.BigDecimal
import java.time.Instant

data class ExchangePriceResult(
    val rate: BigDecimal,
    val timestamp: Instant? = null,
    val isVolatile: Boolean = false
)

interface ExchangePriceProvider {
    val name: String

    /**
     * Fetches the price for the given pair.
     * Returns the rate: 1 base = [rate] quote.
     */
    fun fetchPrice(base: Currency, quote: Currency): ExchangePriceResult?

    /**
     * Returns true if this provider supports the given pair.
     */
    fun supports(base: Currency, quote: Currency): Boolean
}

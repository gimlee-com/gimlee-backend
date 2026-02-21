package com.gimlee.payments.exchange.provider

import com.gimlee.common.domain.model.Currency
import com.gimlee.payments.exchange.domain.ExchangePriceProvider
import com.gimlee.payments.exchange.domain.ExchangePriceResult
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@Order(100)
class FixedPriceProvider : ExchangePriceProvider {
    override val name: String = "Fixed"

    private val rates = mapOf(
        (Currency.USDT to Currency.USD) to BigDecimal.ONE
    )

    override fun supports(base: Currency, quote: Currency): Boolean {
        return rates.containsKey(base to quote)
    }

    override fun fetchPrice(base: Currency, quote: Currency): ExchangePriceResult? {
        return rates[base to quote]?.let { ExchangePriceResult(it) }
    }
}

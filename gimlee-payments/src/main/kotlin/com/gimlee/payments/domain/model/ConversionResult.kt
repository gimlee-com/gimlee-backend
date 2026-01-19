package com.gimlee.payments.domain.model

import com.gimlee.common.domain.model.Currency
import java.math.BigDecimal
import java.time.Instant

data class ConversionStep(
    val baseCurrency: Currency,
    val quoteCurrency: Currency,
    val rate: BigDecimal,
    val sourceExchangeRate: ExchangeRate
)

data class ConversionResult(
    val targetAmount: BigDecimal,
    val from: Currency,
    val to: Currency,
    val steps: List<ConversionStep>,
    val updatedAt: Instant,
    val isVolatile: Boolean
)

class ConversionException(message: String) : RuntimeException(message)

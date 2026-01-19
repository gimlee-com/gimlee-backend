package com.gimlee.payments.domain.model

import com.gimlee.common.domain.model.Currency
import java.math.BigDecimal
import java.time.Instant

data class ExchangeRate(
    val baseCurrency: Currency,
    val quoteCurrency: Currency,
    val rate: BigDecimal,
    val updatedAt: Instant,
    val source: String,
    val isVolatile: Boolean = false
)

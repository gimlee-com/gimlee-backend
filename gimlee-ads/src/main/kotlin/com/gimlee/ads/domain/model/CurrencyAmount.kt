package com.gimlee.ads.domain.model

import java.math.BigDecimal

data class CurrencyAmount(
    val amount: BigDecimal,
    val currency: Currency
)
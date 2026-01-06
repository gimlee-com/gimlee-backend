package com.gimlee.ads.domain.model
import com.gimlee.common.domain.model.Currency

import java.math.BigDecimal

data class CurrencyAmount(
    val amount: BigDecimal,
    val currency: Currency
)
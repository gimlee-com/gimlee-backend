package com.gimlee.ads.web.dto.response
import com.gimlee.common.domain.model.Currency

import com.gimlee.ads.domain.model.CurrencyAmount
import java.math.BigDecimal

data class CurrencyAmountDto(
    val amount: BigDecimal,
    val currency: Currency
) {
    companion object {
        fun fromDomain(currencyAmount: CurrencyAmount?): CurrencyAmountDto? {
            return currencyAmount?.let {
                CurrencyAmountDto(
                    amount = it.amount,
                    currency = it.currency
                )
            }
        }
    }
}
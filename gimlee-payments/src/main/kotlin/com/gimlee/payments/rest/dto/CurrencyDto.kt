package com.gimlee.payments.rest.dto

import com.gimlee.common.domain.model.Currency
import com.gimlee.common.domain.model.CurrencyType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Represents a currency with its localized name and properties")
data class CurrencyDto(
    @Schema(description = "The unique code of the currency", example = "USD")
    val code: String,

    @Schema(description = "The localized name of the currency", example = "US Dollar")
    val name: String,

    @Schema(description = "The type of the currency (FIAT or CRYPTO)", example = "FIAT")
    val type: CurrencyType,

    @Schema(description = "The number of decimal places supported by this currency", example = "2")
    val decimalPlaces: Int
) {
    companion object {
        fun fromDomain(domain: Currency, localizedName: String): CurrencyDto = CurrencyDto(
            code = domain.name,
            name = localizedName,
            type = domain.type,
            decimalPlaces = domain.decimalPlaces
        )
    }
}

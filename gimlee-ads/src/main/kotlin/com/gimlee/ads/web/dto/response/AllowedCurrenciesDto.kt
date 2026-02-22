package com.gimlee.ads.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Available currencies for ad pricing")
data class AllowedCurrenciesDto(
    @Schema(description = "Settlement currencies the user can accept for payments")
    val settlementCurrencies: List<CurrencyInfoDto>,

    @Schema(description = "All currencies available as reference for pegged pricing")
    val referenceCurrencies: List<CurrencyInfoDto>
)

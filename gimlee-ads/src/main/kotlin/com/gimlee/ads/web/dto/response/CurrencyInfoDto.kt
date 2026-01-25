package com.gimlee.ads.web.dto.response

import com.gimlee.common.domain.model.Currency
import io.swagger.v3.oas.annotations.media.Schema

/**
 * DTO representing currency information including its code and localized name.
 */
@Schema(description = "Currency information")
data class CurrencyInfoDto(
    @Schema(description = "Currency code", example = "ARRR")
    val code: Currency,

    @Schema(description = "Localized currency name", example = "Pirate Chain")
    val name: String
)

package com.gimlee.user.web.dto.request

import com.gimlee.common.validation.IetfLanguageTag
import com.gimlee.common.validation.IsoCountryCode
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request DTO for partially updating user preferences. All fields are optional.")
data class PatchUserPreferencesRequestDto(
    @field:IetfLanguageTag
    @Schema(description = "Language tag (e.g., en-US, pl-PL). Optional.", example = "en-US")
    val language: String? = null,

    @Schema(description = "Preferred currency (e.g., USD, EUR, ARRR). Optional.", example = "USD")
    val preferredCurrency: String? = null,

    @field:IsoCountryCode
    @Schema(description = "Country of residence as ISO 3166-1 alpha-2 code (e.g., US, PL). Optional.", example = "US")
    val countryOfResidence: String? = null
)

package com.gimlee.user.web.dto.request

import com.gimlee.common.validation.IetfLanguageTag
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request DTO for partially updating user preferences. All fields are optional.")
data class PatchUserPreferencesRequestDto(
    @field:IetfLanguageTag
    @Schema(description = "Language tag (e.g., en-US, pl-PL). Optional.", example = "en-US")
    val language: String? = null,

    @Schema(description = "Preferred currency (e.g., USD, EUR, ARRR). Optional.", example = "USD")
    val preferredCurrency: String? = null
)

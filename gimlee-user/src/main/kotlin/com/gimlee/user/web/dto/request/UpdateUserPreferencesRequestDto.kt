package com.gimlee.user.web.dto.request

import com.gimlee.common.validation.IetfLanguageTag
import com.gimlee.common.validation.IsoCountryCode
import jakarta.validation.constraints.NotBlank

data class UpdateUserPreferencesRequestDto(
    @field:NotBlank(message = "Language cannot be blank.")
    @field:IetfLanguageTag
    val language: String,

    val preferredCurrency: String?,

    @field:IsoCountryCode
    val countryOfResidence: String? = null
)

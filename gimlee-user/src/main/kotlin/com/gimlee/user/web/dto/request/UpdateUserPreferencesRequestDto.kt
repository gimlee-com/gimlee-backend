package com.gimlee.user.web.dto.request

import com.gimlee.common.validation.IetfLanguageTag
import jakarta.validation.constraints.NotBlank

data class UpdateUserPreferencesRequestDto(
    @field:NotBlank(message = "Language cannot be blank.")
    @field:IetfLanguageTag
    val language: String
)

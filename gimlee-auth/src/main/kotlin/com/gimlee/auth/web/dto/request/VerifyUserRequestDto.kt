package com.gimlee.auth.web.dto.request

import jakarta.validation.constraints.Pattern

data class VerifyUserRequestDto(
    @get:Pattern(regexp = "[0-9]{6}")
    val code: String
)
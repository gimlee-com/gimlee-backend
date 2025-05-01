package com.gimlee.api.auth.web.dto.request

import jakarta.validation.constraints.Email

data class EmailAvailableRequestDto(
    @get:Email
    val email: String
)
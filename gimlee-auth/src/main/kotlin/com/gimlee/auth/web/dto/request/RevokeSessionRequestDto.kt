package com.gimlee.auth.web.dto.request

data class RevokeSessionRequestDto(
    val refreshToken: String
)

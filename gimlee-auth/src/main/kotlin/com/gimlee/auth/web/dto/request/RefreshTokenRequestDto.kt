package com.gimlee.auth.web.dto.request

data class RefreshTokenRequestDto(
    val refreshToken: String,
    val deviceId: String = "unknown"
)

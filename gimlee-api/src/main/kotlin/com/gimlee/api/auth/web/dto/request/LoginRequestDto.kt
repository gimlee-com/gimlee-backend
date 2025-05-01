package com.gimlee.api.auth.web.dto.request

data class LoginRequestDto(
    val username: String,
    val password: String
)
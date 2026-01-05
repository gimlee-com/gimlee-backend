package com.gimlee.auth.domain.auth

data class IdentityVerificationResponse(
    val success: Boolean,
    val status: String? = null,
    val message: String? = null,
    val accessToken: String? = null
)
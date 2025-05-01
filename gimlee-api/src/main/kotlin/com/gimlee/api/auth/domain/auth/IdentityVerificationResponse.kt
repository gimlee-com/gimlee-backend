package com.gimlee.api.auth.domain.auth

class IdentityVerificationResponse(
    val success: Boolean,
    val accessToken: String? = null
) {
    companion object {
        val unsuccessful = IdentityVerificationResponse(success = false)
    }
}
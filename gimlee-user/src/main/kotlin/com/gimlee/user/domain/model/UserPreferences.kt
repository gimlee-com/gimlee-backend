package com.gimlee.user.domain.model

data class UserPreferences(
    val userId: String,
    val language: String, // IETF BCP 47 tag, e.g., en-US
    val preferredCurrency: String? = null
)

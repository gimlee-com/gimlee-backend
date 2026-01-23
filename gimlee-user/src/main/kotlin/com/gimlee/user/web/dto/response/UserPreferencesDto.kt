package com.gimlee.user.web.dto.response

import com.gimlee.user.domain.model.UserPreferences

data class UserPreferencesDto(
    val language: String,
    val preferredCurrency: String?
) {
    companion object {
        fun fromDomain(domain: UserPreferences): UserPreferencesDto = UserPreferencesDto(
            language = domain.language,
            preferredCurrency = domain.preferredCurrency
        )
    }
}

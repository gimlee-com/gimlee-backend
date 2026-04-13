package com.gimlee.api.config

import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.user.domain.UserPreferencesService
import org.springframework.stereotype.Component

@Component
class DefaultUserLanguageProvider(
    private val userPreferencesService: UserPreferencesService
) : UserLanguageProvider {

    override fun getLanguage(userId: String): String {
        return userPreferencesService.getUserPreferences(userId).language
    }
}

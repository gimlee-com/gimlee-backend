package com.gimlee.user.domain

import com.gimlee.user.config.UserPreferencesProperties
import com.gimlee.user.domain.model.UserPreferences
import com.gimlee.user.persistence.UserPreferencesRepository
import com.gimlee.user.persistence.model.UserPreferencesDocument
import org.bson.types.ObjectId
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service

@Service
class UserPreferencesService(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val properties: UserPreferencesProperties
) {

    fun getUserPreferences(userId: String): UserPreferences {
        val document = userPreferencesRepository.findByUserId(ObjectId(userId))
        if (document != null) {
            val domain = document.toDomain()
            return if (domain.preferredCurrency == null) {
                domain.copy(preferredCurrency = getDefaultCurrency())
            } else {
                domain
            }
        }
        return UserPreferences(
            userId = userId,
            language = "en-US",
            preferredCurrency = getDefaultCurrency()
        )
    }

    fun getDefaultCurrency(): String {
        val locale = LocaleContextHolder.getLocale()
        val languageTag = locale.toLanguageTag()
        return properties.currencyMappings[languageTag] ?: properties.defaultCurrency
    }

    fun updateUserPreferences(userId: String, language: String, preferredCurrency: String?): UserPreferences {
        val preferences = UserPreferences(userId, language, preferredCurrency)
        userPreferencesRepository.save(UserPreferencesDocument.fromDomain(preferences))
        return preferences
    }
}

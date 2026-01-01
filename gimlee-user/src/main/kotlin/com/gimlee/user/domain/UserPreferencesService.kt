package com.gimlee.user.domain

import com.gimlee.user.domain.model.UserPreferences
import com.gimlee.user.persistence.UserPreferencesRepository
import com.gimlee.user.persistence.model.UserPreferencesDocument
import org.bson.types.ObjectId
import org.springframework.stereotype.Service

@Service
class UserPreferencesService(private val userPreferencesRepository: UserPreferencesRepository) {

    fun getUserPreferences(userId: String): UserPreferences {
        val document = userPreferencesRepository.findByUserId(ObjectId(userId))
        return document?.toDomain() ?: UserPreferences(userId, "en-US") // Default language
    }

    fun updateUserPreferences(userId: String, language: String): UserPreferences {
        val preferences = UserPreferences(userId, language)
        userPreferencesRepository.save(UserPreferencesDocument.fromDomain(preferences))
        return preferences
    }
}

package com.gimlee.notifications.domain

/**
 * Provides the preferred language for a user. Implemented by the application layer
 * to decouple gimlee-notifications from gimlee-user.
 */
interface UserLanguageProvider {
    fun getLanguage(userId: String): String
}

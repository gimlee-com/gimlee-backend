package com.gimlee.user.persistence.model

import com.gimlee.user.domain.model.UserPreferences
import org.bson.types.ObjectId

data class UserPreferencesDocument(
    val userId: ObjectId,
    val language: String,
    val preferredCurrency: String?
) {
    fun toDomain(): UserPreferences = UserPreferences(
        userId = userId.toHexString(),
        language = language,
        preferredCurrency = preferredCurrency
    )

    companion object {
        const val COLLECTION_NAME = "user-preferences"
        const val FIELD_USER_ID = "_id"
        const val FIELD_LANGUAGE = "lng"
        const val FIELD_PREFERRED_CURRENCY = "pc"

        fun fromDomain(domain: UserPreferences): UserPreferencesDocument = UserPreferencesDocument(
            userId = ObjectId(domain.userId),
            language = domain.language,
            preferredCurrency = domain.preferredCurrency
        )
    }
}

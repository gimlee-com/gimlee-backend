package com.gimlee.user.persistence.model

import com.gimlee.user.domain.model.UserProfile
import org.bson.types.ObjectId

data class UserProfileDocument(
    val userId: ObjectId,
    val avatarUrl: String?,
    val updatedAt: Long
) {
    fun toDomain(): UserProfile = UserProfile(
        userId = userId.toHexString(),
        avatarUrl = avatarUrl,
        updatedAt = updatedAt
    )

    companion object {
        const val COLLECTION_NAME = "user-profiles"
        const val FIELD_USER_ID = "_id"
        const val FIELD_AVATAR_URL = "avt"
        const val FIELD_UPDATED_AT = "upd"

        fun fromDomain(domain: UserProfile): UserProfileDocument = UserProfileDocument(
            userId = ObjectId(domain.userId),
            avatarUrl = domain.avatarUrl,
            updatedAt = domain.updatedAt
        )
    }
}

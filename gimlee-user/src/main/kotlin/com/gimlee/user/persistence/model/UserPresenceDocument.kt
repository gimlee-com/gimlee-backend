package com.gimlee.user.persistence.model

import com.gimlee.user.domain.model.UserPresence
import com.gimlee.user.domain.model.UserPresenceStatus
import org.bson.types.ObjectId

data class UserPresenceDocument(
    val userId: ObjectId,
    val lastSeenAt: Long,
    val status: String,
    val customStatus: String?
) {
    fun toDomain(): UserPresence = UserPresence(
        userId = userId.toHexString(),
        lastSeenAt = lastSeenAt,
        status = UserPresenceStatus.fromShortName(status),
        customStatus = customStatus
    )

    companion object {
        const val COLLECTION_NAME = "user-presence"
        const val FIELD_USER_ID = "_id"
        const val FIELD_LAST_SEEN_AT = "ls"
        const val FIELD_STATUS = "s"
        const val FIELD_CUSTOM_STATUS = "cs"

        fun fromDomain(domain: UserPresence): UserPresenceDocument = UserPresenceDocument(
            userId = ObjectId(domain.userId),
            lastSeenAt = domain.lastSeenAt,
            status = domain.status.shortName,
            customStatus = domain.customStatus
        )
    }
}

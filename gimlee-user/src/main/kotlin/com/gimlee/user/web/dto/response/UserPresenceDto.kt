package com.gimlee.user.web.dto.response

import com.gimlee.user.domain.model.UserPresence
import com.gimlee.user.domain.model.UserPresenceStatus

data class UserPresenceDto(
    val userId: String,
    val lastSeenAt: Long,
    val status: UserPresenceStatus,
    val customStatus: String?
) {
    companion object {
        fun fromDomain(domain: UserPresence): UserPresenceDto = UserPresenceDto(
            userId = domain.userId,
            lastSeenAt = domain.lastSeenAt,
            status = domain.status,
            customStatus = domain.customStatus
        )
    }
}

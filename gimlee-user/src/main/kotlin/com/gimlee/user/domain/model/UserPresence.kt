package com.gimlee.user.domain.model

data class UserPresence(
    val userId: String,
    val lastSeenAt: Long,
    val status: UserPresenceStatus,
    val customStatus: String? = null
)

enum class UserPresenceStatus(val shortName: String) {
    ONLINE("ON"),
    AWAY("AW"),
    BUSY("BS"),
    OFFLINE("OFF");

    companion object {
        fun fromShortName(shortName: String): UserPresenceStatus =
            entries.find { it.shortName == shortName } ?: OFFLINE
    }
}

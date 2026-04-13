package com.gimlee.events

import java.time.Instant

data class UserBannedEvent(
    val userId: String,
    val username: String,
    val email: String,
    val reason: String,
    val bannedBy: String,
    val bannedUntil: Long?,
    val timestamp: Instant = Instant.now()
)

data class UserUnbannedEvent(
    val userId: String,
    val unbannedBy: String,
    val timestamp: Instant = Instant.now()
)

data class BanExpiryApproachingEvent(
    val userId: String,
    val bannedUntil: Long,
    val timestamp: Instant = Instant.now()
)

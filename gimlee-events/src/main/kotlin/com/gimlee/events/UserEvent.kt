package com.gimlee.events

import java.time.Instant

/**
 * Event published when a user is active.
 */
data class UserActivityEvent(
    val userId: String,
    val timestamp: Instant = Instant.now()
)

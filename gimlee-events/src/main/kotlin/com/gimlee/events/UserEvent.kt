package com.gimlee.events

import java.time.Instant

/**
 * Event published when a user is active.
 */
data class UserActivityEvent(
    val userId: String,
    val timestamp: Instant = Instant.now()
)

/**
 * Event published when a new user account is created during registration.
 */
data class UserRegisteredEvent(
    val userId: String,
    val countryOfResidence: String? = null,
    val timestamp: Instant = Instant.now()
)

package com.gimlee.events

import java.time.Instant

/**
 * Event published when an ad's status changes (e.g., activated, deactivated).
 */
data class AdStatusChangedEvent(
    val adId: String,
    val oldStatus: String?,
    val newStatus: String,
    val categoryIds: List<Int>,
    val timestamp: Instant = Instant.now()
)

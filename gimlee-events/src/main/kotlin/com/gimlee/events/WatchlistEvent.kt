package com.gimlee.events

import java.time.Instant

enum class WatchlistEventType {
    ADDED, REMOVED
}

data class AdWatchlistEvent(
    val userId: String,
    val adId: String,
    val type: WatchlistEventType,
    val timestamp: Instant = Instant.now()
)

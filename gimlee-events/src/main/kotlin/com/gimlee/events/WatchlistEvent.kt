package com.gimlee.events

import java.time.Instant

/**
 * Event published when a user adds an ad to their watchlist.
 */
data class AdAddedToWatchlistEvent(
    val userId: String,
    val adId: String,
    val timestamp: Instant = Instant.now()
)

/**
 * Event published when a user removes an ad from their watchlist.
 */
data class AdRemovedFromWatchlistEvent(
    val userId: String,
    val adId: String,
    val timestamp: Instant = Instant.now()
)

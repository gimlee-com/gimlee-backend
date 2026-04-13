package com.gimlee.events

import java.time.Instant

/**
 * Event published when an ad's status changes (e.g., activated, deactivated).
 */
data class AdStatusChangedEvent(
    val adId: String,
    val sellerId: String? = null,
    val oldStatus: String?,
    val newStatus: String,
    val categoryIds: List<Int>,
    val reason: Reason = Reason.USER_ACTION,
    val timestamp: Instant = Instant.now()
) {
    enum class Reason {
        USER_ACTION,
        STOCK_DEPLETED,
        CATEGORY_HIDDEN,
        USER_BANNED
    }
}

data class AdPriceChangedEvent(
    val adId: String,
    val sellerId: String,
    val adTitle: String,
    val oldPrice: String,
    val newPrice: String,
    val currency: String,
    val timestamp: Instant = Instant.now()
)

data class AdRestockedEvent(
    val adId: String,
    val sellerId: String,
    val adTitle: String,
    val newStock: Int,
    val timestamp: Instant = Instant.now()
)

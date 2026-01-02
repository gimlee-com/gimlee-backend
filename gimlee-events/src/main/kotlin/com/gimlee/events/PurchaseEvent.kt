package com.gimlee.events

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

data class PurchaseEvent(
    val purchaseId: ObjectId,
    val items: List<PurchaseEventItem>,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val status: Int,
    val totalAmount: BigDecimal,
    val timestamp: Instant,
)

data class PurchaseEventItem(
    val adId: ObjectId,
    val quantity: Int
)

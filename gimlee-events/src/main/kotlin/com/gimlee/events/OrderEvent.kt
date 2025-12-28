package com.gimlee.events

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

data class OrderEvent(
    val orderId: ObjectId,
    val adId: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val status: Int,
    val amount: BigDecimal,
    val timestamp: Instant,
)

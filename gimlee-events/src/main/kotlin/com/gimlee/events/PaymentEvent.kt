package com.gimlee.events

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

data class PaymentEvent(
    val purchaseId: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val status: Int,
    val paymentMethod: Int,
    val amount: BigDecimal,
    val timestamp: Instant,
)

data class PaymentDeadlineApproachingEvent(
    val purchaseId: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val amount: BigDecimal,
    val deadline: Instant,
)
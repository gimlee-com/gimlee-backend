package com.gimlee.events

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

data class PaymentEvent(
    val orderId: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val status: Int,
    val paymentMethod: Int,
    val amount: BigDecimal,
    val timestamp: Instant,
)
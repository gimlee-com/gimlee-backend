package com.gimlee.orders.domain.model

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

data class Order(
    val id: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val adId: ObjectId,
    val amount: BigDecimal,
    val status: OrderStatus,
    val createdAt: Instant
)

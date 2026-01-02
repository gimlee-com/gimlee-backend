package com.gimlee.purchases.domain.model

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

data class Purchase(
    val id: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val adId: ObjectId,
    val amount: BigDecimal,
    val status: PurchaseStatus,
    val createdAt: Instant
)

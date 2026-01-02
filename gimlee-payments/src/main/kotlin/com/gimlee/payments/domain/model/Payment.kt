package com.gimlee.payments.domain.model

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

data class Payment(
    val id: ObjectId,
    val purchaseId: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val paymentMethod: PaymentMethod,
    val memo: String,
    val deadline: Instant,
    val receivingAddress: String, // Was sellerZAddress
    val createdAt: Instant
)

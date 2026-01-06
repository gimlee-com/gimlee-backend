package com.gimlee.purchases.domain.model
import com.gimlee.common.domain.model.Currency

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.Instant

data class Purchase(
    val id: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val items: List<PurchaseItem>,
    val totalAmount: BigDecimal,
    val status: PurchaseStatus,
    val createdAt: Instant
)

data class PurchaseItem(
    val adId: ObjectId,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val currency: Currency
)

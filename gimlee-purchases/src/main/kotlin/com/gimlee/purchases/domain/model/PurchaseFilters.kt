package com.gimlee.purchases.domain.model

import org.bson.types.ObjectId

data class PurchaseFilters(
    val statuses: List<PurchaseStatus>? = null,
    val purchaseId: ObjectId? = null,
    val adId: ObjectId? = null,
    val fromMicros: Long? = null,
    val toMicros: Long? = null,
    val buyerIds: List<ObjectId>? = null,
    val sellerIds: List<ObjectId>? = null,
    val noResults: Boolean = false
)

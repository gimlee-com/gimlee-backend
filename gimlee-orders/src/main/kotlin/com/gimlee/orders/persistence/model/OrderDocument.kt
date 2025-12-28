package com.gimlee.orders.persistence.model

import org.bson.types.Decimal128
import org.bson.types.ObjectId

/**
 * MongoDB document representing an order.
 */
data class OrderDocument(
    val id: ObjectId = ObjectId.get(),
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val adId: ObjectId,
    val amount: Decimal128,
    val status: Int,
    val createdAtMicros: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_BUYER_ID = "bid"
        const val FIELD_SELLER_ID = "sid"
        const val FIELD_AD_ID = "aid"
        const val FIELD_AMOUNT = "amt"
        const val FIELD_STATUS = "st"
        const val FIELD_CREATED_AT = "ca"
    }
}

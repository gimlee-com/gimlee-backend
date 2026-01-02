package com.gimlee.purchases.persistence.model

import org.bson.types.Decimal128
import org.bson.types.ObjectId

/**
 * MongoDB document representing a purchase.
 */
data class PurchaseDocument(
    val id: ObjectId = ObjectId.get(),
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val items: List<PurchaseItemDocument>,
    val totalAmount: Decimal128,
    val status: Int,
    val createdAtMicros: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_BUYER_ID = "bid"
        const val FIELD_SELLER_ID = "sid"
        const val FIELD_ITEMS = "its"
        const val FIELD_TOTAL_AMOUNT = "tamt"
        const val FIELD_STATUS = "st"
        const val FIELD_CREATED_AT = "ca"
    }
}

data class PurchaseItemDocument(
    val adId: ObjectId,
    val quantity: Int,
    val unitPrice: Decimal128,
    val currency: String
) {
    companion object {
        const val FIELD_AD_ID = "aid"
        const val FIELD_QUANTITY = "qty"
        const val FIELD_UNIT_PRICE = "up"
        const val FIELD_CURRENCY = "cur"
    }
}

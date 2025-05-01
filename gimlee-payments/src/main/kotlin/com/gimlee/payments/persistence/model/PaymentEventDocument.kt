package com.gimlee.payments.persistence.model

import org.bson.types.Decimal128
import org.bson.types.ObjectId

/**
 * MongoDB document representing a payment event.
 */
data class PaymentEventDocument(
    val id: ObjectId = ObjectId.get(),
    val orderId: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val status: Int,
    val paymentMethod: Int,
    val amount: Decimal128,
    val timestampMicros: Long,
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_ORDER_ID = "oid"
        const val FIELD_BUYER_ID = "bid"
        const val FIELD_SELLER_ID = "sid"
        const val FIELD_STATUS = "st"
        const val FIELD_PAYMENT_METHOD = "pm"
        const val FIELD_AMOUNT = "amt"
        const val FIELD_TIMESTAMP = "ts"
    }
}
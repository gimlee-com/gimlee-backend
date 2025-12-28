package com.gimlee.payments.persistence.model

import org.bson.types.Decimal128
import org.bson.types.ObjectId

/**
 * MongoDB document representing a payment transaction.
 */
data class PaymentDocument(
    val id: ObjectId = ObjectId.get(),
    val orderId: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val amount: Decimal128,
    val status: Int,
    val paymentMethod: Int,
    val memo: String,
    val deadlineMicros: Long,
    val receivingAddress: String,
    val createdAtMicros: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_ORDER_ID = "oid"
        const val FIELD_BUYER_ID = "bid"
        const val FIELD_SELLER_ID = "sid"
        const val FIELD_AMOUNT = "amt"
        const val FIELD_STATUS = "st"
        const val FIELD_PAYMENT_METHOD = "pm"
        const val FIELD_MEMO = "mm"
        const val FIELD_DEADLINE = "dl"
        const val FIELD_RECEIVING_ADDRESS = "ra"
        const val FIELD_CREATED_AT = "ca"
    }
}

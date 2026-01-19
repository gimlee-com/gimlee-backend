package com.gimlee.payments.persistence.model

import org.bson.types.Decimal128
import org.bson.types.ObjectId

data class ExchangeRateDocument(
    val id: ObjectId = ObjectId.get(),
    val baseCurrency: String,
    val quoteCurrency: String,
    val rate: Decimal128,
    val updatedAtMicros: Long,
    val source: String,
    val isVolatile: Boolean = false
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_BASE_CURRENCY = "bc"
        const val FIELD_QUOTE_CURRENCY = "qc"
        const val FIELD_RATE = "r"
        const val FIELD_UPDATED_AT = "ua"
        const val FIELD_SOURCE = "src"
        const val FIELD_IS_VOLATILE = "iv"
    }
}

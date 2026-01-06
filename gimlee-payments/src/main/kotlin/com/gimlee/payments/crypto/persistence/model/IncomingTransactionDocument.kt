package com.gimlee.payments.crypto.persistence.model
import com.gimlee.common.domain.model.Currency

import org.bson.types.ObjectId
import com.gimlee.common.toMicros
import java.time.Instant

/**
 * MongoDB document representing an incoming cryptocurrency transaction
 * associated with a specific user and Z-address.
 * Mapping is configured programmatically in MongoMappingConfig.
 */
data class IncomingTransactionDocument(
    val id: ObjectId = ObjectId.get(),
    val type: Currency,
    val userId: ObjectId,
    val address: String,
    val txid: String,
    val memo: String?,
    val amount: Double,
    val confirmations: Int,
    val detectedAtMicros: Long = Instant.now().toMicros() // Timestamp (microseconds since epoch)
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_TYPE = "t"
        const val FIELD_USER_ID = "uid"
        const val FIELD_ADDRESS = "addr"
        const val FIELD_TXID = "txid"
        const val FIELD_MEMO = "m"
        const val FIELD_AMOUNT = "amt"
        const val FIELD_CONFIRMATIONS = "cfrm"
        const val FIELD_DETECTED_AT = "detTs"
    }
}
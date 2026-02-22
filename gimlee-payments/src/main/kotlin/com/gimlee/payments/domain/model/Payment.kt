package com.gimlee.payments.domain.model

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

data class Payment(
    val id: ObjectId,
    val purchaseId: ObjectId,
    val buyerId: ObjectId,
    val sellerId: ObjectId,
    val amount: BigDecimal,
    val paidAmount: BigDecimal = BigDecimal.ZERO,
    val status: PaymentStatus,
    val paymentMethod: PaymentMethod,
    val memo: String,
    val deadline: Instant,
    val receivingAddress: String, // Was sellerZAddress
    val createdAt: Instant
) {
    val qrCodeUri: String
        get() = if (paymentMethod == PaymentMethod.PIRATE_CHAIN) {
            val encodedMemo = URLEncoder.encode(memo, StandardCharsets.UTF_8.toString())
            "pirate:$receivingAddress?amount=${amount.stripTrailingZeros().toPlainString()}&memo=$encodedMemo"
        } else {
            receivingAddress
        }
}

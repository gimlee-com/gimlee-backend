package com.gimlee.payments.domain.model

import com.gimlee.payments.domain.model.Payment
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class PaymentQrCodeTest {

    @Test
    fun `should generate correct QR code URI for PirateChain`() {
        val amount = BigDecimal("10.50000000") // Should strip trailing zeros
        val memo = "Hello World!"
        val address = "zs1pirateaddress"
        
        val payment = Payment(
            id = ObjectId.get(),
            purchaseId = ObjectId.get(),
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            amount = amount,
            paidAmount = BigDecimal.ZERO,
            status = PaymentStatus.AWAITING_CONFIRMATION,
            paymentMethod = PaymentMethod.PIRATE_CHAIN,
            memo = memo,
            deadline = Instant.now(),
            receivingAddress = address,
            createdAt = Instant.now()
        )

        val encodedMemo = URLEncoder.encode(memo, StandardCharsets.UTF_8.toString())
        val expectedUri = "pirate:$address?amount=10.5&memo=$encodedMemo"

        assertEquals(expectedUri, payment.qrCodeUri)
    }

    @Test
    fun `should fall back to address for non-PirateChain payments`() {
        val address = "ys1ycashaddress"
        val payment = Payment(
            id = ObjectId.get(),
            purchaseId = ObjectId.get(),
            buyerId = ObjectId.get(),
            sellerId = ObjectId.get(),
            amount = BigDecimal("10.0"),
            paidAmount = BigDecimal.ZERO,
            status = PaymentStatus.AWAITING_CONFIRMATION,
            paymentMethod = PaymentMethod.YCASH,
            memo = "memo",
            deadline = Instant.now(),
            receivingAddress = address,
            createdAt = Instant.now()
        )

        assertEquals(address, payment.qrCodeUri)
    }
}

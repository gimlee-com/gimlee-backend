package com.gimlee.api.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "Summary of payment information for a purchase")
data class PaymentSummaryDto(
    @field:Schema(description = "Total payment amount expected")
    val amount: BigDecimal,
    @field:Schema(description = "Amount paid so far")
    val paidAmount: BigDecimal,
    @field:Schema(description = "Receiving wallet address")
    val address: String,
    @field:Schema(description = "Payment memo for identification")
    val memo: String,
    @field:Schema(description = "Payment deadline after which the purchase may fail")
    val deadline: Instant,
    @field:Schema(description = "QR code URI for the payment")
    val qrCodeUri: String
)

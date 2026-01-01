package com.gimlee.api.web.dto

import java.math.BigDecimal

data class OrderResponseDto(
    val orderId: String,
    val status: String,
    val payment: PaymentDetailsDto?
)

data class PaymentDetailsDto(
    val address: String,
    val amount: BigDecimal,
    val memo: String,
    val qrCodeUri: String
)

data class OrderStatusResponseDto(
    val orderId: String,
    val status: String,
    val paymentStatus: String?
)

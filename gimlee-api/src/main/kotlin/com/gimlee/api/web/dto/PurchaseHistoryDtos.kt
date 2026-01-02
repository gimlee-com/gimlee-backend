package com.gimlee.api.web.dto

import java.math.BigDecimal
import java.time.Instant

data class PurchaseHistoryDto(
    val id: String,
    val status: String,
    val paymentStatus: String?,
    val createdAt: Instant,
    val totalAmount: BigDecimal,
    val currency: String,
    val items: List<SalesOrderItemDto>,
    val seller: SellerInfoDto
)

data class SellerInfoDto(
    val id: String,
    val username: String
)

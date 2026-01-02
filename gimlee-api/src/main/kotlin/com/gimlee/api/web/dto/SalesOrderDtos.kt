package com.gimlee.api.web.dto

import java.math.BigDecimal
import java.time.Instant

data class SalesOrderDto(
    val id: String,
    val status: String,
    val paymentStatus: String?,
    val createdAt: Instant,
    val totalAmount: BigDecimal,
    val currency: String,
    val items: List<SalesOrderItemDto>,
    val buyer: BuyerInfoDto
)

data class SalesOrderItemDto(
    val adId: String,
    val title: String,
    val quantity: Int,
    val unitPrice: BigDecimal
)

data class BuyerInfoDto(
    val id: String,
    val username: String
)

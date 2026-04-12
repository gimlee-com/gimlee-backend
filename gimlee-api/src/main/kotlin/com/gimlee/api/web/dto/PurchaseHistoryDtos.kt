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
    val seller: SellerInfoDto,
    val deliveryAddress: DeliveryAddressSnapshotDto?
)

data class DeliveryAddressSnapshotDto(
    val name: String,
    val fullName: String,
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String,
    val phoneNumber: String
)

data class SellerInfoDto(
    val id: String,
    val username: String
)

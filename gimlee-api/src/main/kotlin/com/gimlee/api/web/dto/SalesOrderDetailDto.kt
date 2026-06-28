package com.gimlee.api.web.dto

import com.gimlee.payments.crypto.web.dto.CryptoTransactionDto
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "Full detail view of a sales order for the seller")
data class SalesOrderDetailDto(
    @field:Schema(description = "Unique purchase/order ID")
    val id: String,
    @field:Schema(description = "Buyer information with username and avatar")
    val buyer: UserSummaryDto,
    @field:Schema(description = "Detailed items in this order including thumbnails")
    val items: List<OrderItemDetailDto>,
    @field:Schema(description = "Total order amount in the settlement currency")
    val totalAmount: BigDecimal,
    @field:Schema(description = "Settlement currency code")
    val currency: String,
    @field:Schema(description = "Current purchase status")
    val status: String,
    @field:Schema(description = "Current payment status, if a payment exists")
    val paymentStatus: String?,
    @field:Schema(description = "Delivery address snapshot, if applicable")
    val deliveryAddress: DeliveryAddressSnapshotDto?,
    @field:Schema(description = "Chronological history of status changes")
    val statusHistory: List<StatusChangeDto>,
    @field:Schema(description = "Cryptocurrency transactions associated with this order")
    val cryptoTransactions: List<CryptoTransactionDto> = emptyList(),
    @field:Schema(description = "Timestamp when the order was created")
    val createdAt: Instant
)

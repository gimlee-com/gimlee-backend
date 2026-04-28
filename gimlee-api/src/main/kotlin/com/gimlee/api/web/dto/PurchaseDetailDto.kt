package com.gimlee.api.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "Full detail view of a purchase for the buyer")
data class PurchaseDetailDto(
    @field:Schema(description = "Unique purchase ID")
    val id: String,
    @field:Schema(description = "Seller information with username and avatar")
    val seller: UserSummaryDto,
    @field:Schema(description = "Detailed items in this purchase including thumbnails")
    val items: List<OrderItemDetailDto>,
    @field:Schema(description = "Total purchase amount in the settlement currency")
    val totalAmount: BigDecimal,
    @field:Schema(description = "Settlement currency code")
    val currency: String,
    @field:Schema(description = "Current purchase status")
    val status: String,
    @field:Schema(description = "Current payment status, if a payment exists")
    val paymentStatus: String?,
    @field:Schema(description = "Delivery address snapshot, if applicable")
    val deliveryAddress: DeliveryAddressSnapshotDto?,
    @field:Schema(description = "Payment details for this purchase, if a payment exists")
    val payment: PaymentSummaryDto?,
    @field:Schema(description = "Chronological history of status changes")
    val statusHistory: List<StatusChangeDto>,
    @field:Schema(description = "Timestamp when the purchase was created")
    val createdAt: Instant
)

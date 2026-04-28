package com.gimlee.api.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "Summary of a sales order in a list view")
data class SalesOrderDto(
    @field:Schema(description = "Unique order ID")
    val id: String,
    @field:Schema(description = "Current purchase status")
    val status: String,
    @field:Schema(description = "Current payment status, if a payment exists")
    val paymentStatus: String?,
    @field:Schema(description = "Timestamp when the order was created")
    val createdAt: Instant,
    @field:Schema(description = "Total order amount in the settlement currency")
    val totalAmount: BigDecimal,
    @field:Schema(description = "Settlement currency code")
    val currency: String,
    @field:Schema(description = "Items in this order")
    val items: List<SalesOrderItemDto>,
    @field:Schema(description = "Buyer information with username and avatar")
    val buyer: UserSummaryDto,
    @field:Schema(description = "Thumbnail path of the first item's ad photo for list display")
    val primaryThumbnailPath: String? = null,
    @field:Schema(description = "Number of distinct items in the order")
    val itemCount: Int = 0,
    @field:Schema(description = "Delivery address snapshot, if applicable")
    val deliveryAddress: DeliveryAddressSnapshotDto? = null
)

@Schema(description = "Summary of an item within a sales order")
data class SalesOrderItemDto(
    @field:Schema(description = "ID of the ad this item refers to")
    val adId: String,
    @field:Schema(description = "Title of the ad")
    val title: String,
    @field:Schema(description = "Quantity purchased")
    val quantity: Int,
    @field:Schema(description = "Unit price in the settlement currency")
    val unitPrice: BigDecimal
)

@Deprecated("Use UserSummaryDto instead", replaceWith = ReplaceWith("UserSummaryDto"))
@Schema(description = "Buyer information. Deprecated — use UserSummaryDto which includes avatar.")
data class BuyerInfoDto(
    val id: String,
    val username: String
)

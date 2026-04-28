package com.gimlee.api.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant

@Schema(description = "Summary of a purchase in the buyer's history list view")
data class PurchaseHistoryDto(
    @field:Schema(description = "Unique purchase ID")
    val id: String,
    @field:Schema(description = "Current purchase status")
    val status: String,
    @field:Schema(description = "Current payment status, if a payment exists")
    val paymentStatus: String?,
    @field:Schema(description = "Timestamp when the purchase was created")
    val createdAt: Instant,
    @field:Schema(description = "Total purchase amount in the settlement currency")
    val totalAmount: BigDecimal,
    @field:Schema(description = "Settlement currency code")
    val currency: String,
    @field:Schema(description = "Items in this purchase")
    val items: List<SalesOrderItemDto>,
    @field:Schema(description = "Seller information with username and avatar")
    val seller: UserSummaryDto,
    @field:Schema(description = "Thumbnail path of the first item's ad photo for list display")
    val primaryThumbnailPath: String? = null,
    @field:Schema(description = "Number of distinct items in the purchase")
    val itemCount: Int = 0,
    @field:Schema(description = "Delivery address snapshot, if applicable")
    val deliveryAddress: DeliveryAddressSnapshotDto? = null
)

@Schema(description = "Snapshot of the delivery address at the time of purchase")
data class DeliveryAddressSnapshotDto(
    val name: String,
    val fullName: String,
    val street: String,
    val city: String,
    val postalCode: String,
    val country: String,
    val phoneNumber: String
)

@Deprecated("Use UserSummaryDto instead", replaceWith = ReplaceWith("UserSummaryDto"))
@Schema(description = "Seller information. Deprecated — use UserSummaryDto which includes avatar.")
data class SellerInfoDto(
    val id: String,
    val username: String
)

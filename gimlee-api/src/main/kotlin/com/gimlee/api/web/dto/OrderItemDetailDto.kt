package com.gimlee.api.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Detailed item within an order, including ad thumbnail for display")
data class OrderItemDetailDto(
    @field:Schema(description = "ID of the ad this item refers to")
    val adId: String,
    @field:Schema(description = "Title of the ad at the time of purchase")
    val title: String,
    @field:Schema(description = "Path to the ad's main photo thumbnail")
    val thumbnailPath: String?,
    @field:Schema(description = "Quantity purchased")
    val quantity: Int,
    @field:Schema(description = "Unit price in the settlement currency")
    val unitPrice: BigDecimal
)

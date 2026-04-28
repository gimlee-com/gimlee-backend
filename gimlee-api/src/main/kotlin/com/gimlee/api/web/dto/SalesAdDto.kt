package com.gimlee.api.web.dto

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.gimlee.ads.web.dto.response.AdDto
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ad with seller-specific statistics for the sales dashboard. " +
        "Includes all standard AdDto fields plus view and order counts.")
data class SalesAdDto(
    @field:JsonUnwrapped
    @field:Schema(hidden = true)
    val ad: AdDto,
    @field:Schema(description = "Total unique page views for this ad")
    val viewsCount: Long,
    @field:Schema(description = "Number of non-cancelled/non-failed orders containing this ad")
    val ordersCount: Long
)

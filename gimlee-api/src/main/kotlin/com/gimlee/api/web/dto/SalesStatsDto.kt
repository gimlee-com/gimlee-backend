package com.gimlee.api.web.dto

import com.gimlee.ads.web.dto.response.CurrencyAmountDto
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Aggregated sales statistics for the seller dashboard")
data class SalesStatsDto(
    @field:Schema(description = "Total revenue per settlement currency for the selected period")
    val revenue: List<CurrencyAmountDto>,
    @field:Schema(description = "Number of orders in an active state (awaiting payment)")
    val activeOrdersCount: Long,
    @field:Schema(description = "Number of completed orders")
    val completedOrdersCount: Long,
    @field:Schema(description = "Total number of ads owned by the seller")
    val totalAdsCount: Long,
    @field:Schema(description = "Number of currently active ads")
    val activeAdsCount: Long,
    @field:Schema(description = "Time period for the statistics")
    val period: StatsPeriod
)

@Schema(description = "Time period for statistics aggregation")
enum class StatsPeriod {
    DAILY,
    MONTHLY,
    YEARLY,
    ALL_TIME
}

package com.gimlee.api.web.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Statistics about ad visits")
data class AdVisitStatsDto(
    @Schema(description = "Daily visit counts for the last 30 days (YYYY-MM-DD -> count)")
    val daily: Map<String, Int>,
    
    @Schema(description = "Total unique visits for the current month")
    val monthly: Long,
    
    @Schema(description = "Total unique visits for the current year")
    val yearly: Long,
    
    @Schema(description = "Grand total unique visits recorded")
    val total: Long
)

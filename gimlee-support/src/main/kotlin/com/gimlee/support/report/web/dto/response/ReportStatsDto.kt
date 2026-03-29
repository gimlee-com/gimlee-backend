package com.gimlee.support.report.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Report dashboard statistics")
data class ReportStatsDto(
    @Schema(description = "Number of open reports") val open: Long,
    @Schema(description = "Number of reports in review") val inReview: Long,
    @Schema(description = "Reports resolved today") val resolvedToday: Long,
    @Schema(description = "Total unresolved reports (open + in review)") val totalUnresolved: Long
)

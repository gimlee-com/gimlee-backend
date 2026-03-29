package com.gimlee.support.report.web.dto.response

import com.gimlee.support.report.domain.model.ReportTimelineAction
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Report timeline entry")
data class ReportTimelineEntryDto(
    @Schema(description = "Entry ID") val id: String,
    @Schema(description = "Action type") val action: ReportTimelineAction,
    @Schema(description = "Username of who performed the action") val performedByUsername: String?,
    @Schema(description = "Action detail") val detail: String?,
    @Schema(description = "Timestamp (epoch micros)") val createdAt: Long
)

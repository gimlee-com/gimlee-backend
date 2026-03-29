package com.gimlee.support.report.web.dto.response

import com.gimlee.support.report.domain.model.ReportReason
import com.gimlee.support.report.domain.model.ReportResolution
import com.gimlee.support.report.domain.model.ReportStatus
import com.gimlee.support.report.domain.model.ReportTargetType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Full report detail with timeline")
data class ReportDetailDto(
    @Schema(description = "Report ID") val id: String,
    @Schema(description = "Type of reported content") val targetType: ReportTargetType,
    @Schema(description = "ID of reported content") val targetId: String,
    @Schema(description = "Title/excerpt of reported content") val targetTitle: String?,
    @Schema(description = "Reason for report") val reason: ReportReason,
    @Schema(description = "Report status") val status: ReportStatus,
    @Schema(description = "Reporter username") val reporterUsername: String?,
    @Schema(description = "Reporter user ID") val reporterUserId: String,
    @Schema(description = "Assignee username") val assigneeUsername: String?,
    @Schema(description = "Assignee user ID") val assigneeUserId: String?,
    @Schema(description = "Created timestamp (epoch micros)") val createdAt: Long,
    @Schema(description = "Updated timestamp (epoch micros)") val updatedAt: Long,
    @Schema(description = "Number of reports for the same target") val siblingCount: Long,
    @Schema(description = "Reporter's description") val description: String?,
    @Schema(description = "Snapshot of reported content at time of report") val targetSnapshot: Map<String, Any?>?,
    @Schema(description = "Resolution type") val resolution: ReportResolution?,
    @Schema(description = "Username of resolver") val resolvedByUsername: String?,
    @Schema(description = "Resolution timestamp (epoch micros)") val resolvedAt: Long?,
    @Schema(description = "Internal notes (admin only)") val internalNotes: String?,
    @Schema(description = "Report activity timeline") val timeline: List<ReportTimelineEntryDto>
)

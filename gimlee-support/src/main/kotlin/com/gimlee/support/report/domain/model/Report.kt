package com.gimlee.support.report.domain.model

data class Report(
    val id: String,
    val targetId: String,
    val targetType: ReportTargetType,
    val contextId: String?,
    val reporterId: String,
    val reason: ReportReason,
    val description: String?,
    val status: ReportStatus,
    val targetTitle: String?,
    val targetSnapshot: Map<String, Any?>?,
    val assigneeId: String?,
    val resolution: ReportResolution?,
    val resolvedBy: String?,
    val resolvedAt: Long?,
    val internalNotes: String?,
    val siblingCount: Long,
    val timeline: List<ReportTimelineEntry>,
    val createdAt: Long,
    val updatedAt: Long
)

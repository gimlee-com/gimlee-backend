package com.gimlee.support.report.domain.model

data class ReportTimelineEntry(
    val id: String,
    val action: ReportTimelineAction,
    val performedBy: String,
    val detail: String?,
    val createdAt: Long
)

package com.gimlee.reports.domain.model

import java.time.Instant

data class Report(
    val id: String,
    val targetId: String,
    val targetType: ReportTargetType,
    val contextId: String?,
    val reporterId: String,
    val reason: String,
    val createdAt: Instant
)

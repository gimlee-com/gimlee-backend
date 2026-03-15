package com.gimlee.reports.domain.model

data class ReportTargetInfo(
    val targetId: String,
    val targetType: ReportTargetType,
    val contextId: String? = null
)

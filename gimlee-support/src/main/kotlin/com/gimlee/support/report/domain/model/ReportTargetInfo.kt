package com.gimlee.support.report.domain.model

data class ReportTargetInfo(
    val targetId: String,
    val targetType: ReportTargetType,
    val contextId: String? = null,
    val targetTitle: String,
    val snapshot: Map<String, Any?> = emptyMap()
)

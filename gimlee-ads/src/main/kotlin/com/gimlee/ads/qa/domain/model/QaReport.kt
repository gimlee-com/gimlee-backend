package com.gimlee.ads.qa.domain.model

import java.time.Instant

data class QaReport(
    val id: String,
    val targetId: String,
    val targetType: QaReportTargetType,
    val adId: String,
    val reporterId: String,
    val reason: String,
    val createdAt: Instant
)

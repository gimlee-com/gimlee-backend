package com.gimlee.support.report.web.dto.response

import com.gimlee.support.report.domain.model.ReportReason
import com.gimlee.support.report.domain.model.ReportTargetType

data class ReportReasonDto(
    val reason: ReportReason,
    val supportedTargets: Set<ReportTargetType>
) {
    companion object {
        fun from(reason: ReportReason): ReportReasonDto =
            ReportReasonDto(reason = reason, supportedTargets = reason.supportedTargets)
    }
}

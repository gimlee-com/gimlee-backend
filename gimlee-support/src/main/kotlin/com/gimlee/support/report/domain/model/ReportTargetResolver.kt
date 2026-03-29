package com.gimlee.support.report.domain.model

interface ReportTargetResolver {
    fun supports(targetType: ReportTargetType): Boolean
    fun resolve(targetType: ReportTargetType, targetId: String): ReportTargetInfo?
}

package com.gimlee.reports.domain.model

/**
 * Implementations validate that a report target exists and provide contextual
 * information. Each module registers its own resolver for the target types it owns.
 */
interface ReportTargetResolver {
    fun supports(targetType: ReportTargetType): Boolean
    fun resolve(targetType: ReportTargetType, targetId: String): ReportTargetInfo?
}

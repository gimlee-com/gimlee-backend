package com.gimlee.support.report.domain.model

import com.gimlee.support.report.domain.model.ReportTargetType.*

enum class ReportReason(
    val shortName: String,
    val supportedTargets: Set<ReportTargetType>
) {
    SPAM("SP", setOf(AD, USER, MESSAGE, QUESTION, ANSWER)),
    FRAUD("FR", setOf(AD, USER, MESSAGE)),
    INAPPROPRIATE_CONTENT("IC", setOf(AD, USER, MESSAGE, QUESTION, ANSWER)),
    COUNTERFEIT("CF", setOf(AD)),
    HARASSMENT("HR", setOf(USER, MESSAGE, QUESTION, ANSWER)),
    COPYRIGHT("CR", setOf(AD, MESSAGE, QUESTION, ANSWER)),
    WRONG_CATEGORY("WC", setOf(AD)),
    OTHER("OT", setOf(AD, USER, MESSAGE, QUESTION, ANSWER));

    fun supportsTarget(targetType: ReportTargetType): Boolean = targetType in supportedTargets

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): ReportReason =
            map[sn] ?: throw IllegalArgumentException("Unknown ReportReason: $sn")

        fun forTargetType(targetType: ReportTargetType): List<ReportReason> =
            entries.filter { it.supportsTarget(targetType) }
    }
}

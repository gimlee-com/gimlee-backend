package com.gimlee.reports.domain.model

enum class ReportTargetType(val shortName: String) {
    QUESTION("Q"),
    ANSWER("A");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): ReportTargetType =
            map[sn] ?: throw IllegalArgumentException("Unknown ReportTargetType: $sn")
    }
}

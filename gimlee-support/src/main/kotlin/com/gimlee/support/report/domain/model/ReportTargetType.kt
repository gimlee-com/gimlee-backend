package com.gimlee.support.report.domain.model

enum class ReportTargetType(val shortName: String) {
    AD("A"),
    USER("U"),
    MESSAGE("M"),
    QUESTION("Q"),
    ANSWER("AN");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): ReportTargetType =
            map[sn] ?: throw IllegalArgumentException("Unknown ReportTargetType: $sn")
    }
}

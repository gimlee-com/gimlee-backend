package com.gimlee.ads.qa.domain.model

enum class QaReportTargetType(val shortName: String) {
    QUESTION("Q"),
    ANSWER("A");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): QaReportTargetType =
            map[sn] ?: throw IllegalArgumentException("Unknown QaReportTargetType: $sn")
    }
}

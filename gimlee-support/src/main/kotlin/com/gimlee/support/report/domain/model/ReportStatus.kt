package com.gimlee.support.report.domain.model

enum class ReportStatus(val shortName: String) {
    OPEN("O"),
    IN_REVIEW("IR"),
    RESOLVED("R"),
    DISMISSED("D");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): ReportStatus =
            map[sn] ?: throw IllegalArgumentException("Unknown ReportStatus: $sn")
    }
}

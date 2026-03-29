package com.gimlee.support.report.domain.model

enum class ReportResolution(val shortName: String) {
    CONTENT_REMOVED("CR"),
    USER_WARNED("UW"),
    USER_BANNED("UB"),
    NO_VIOLATION("NV"),
    DUPLICATE("DU"),
    OTHER("OT");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): ReportResolution =
            map[sn] ?: throw IllegalArgumentException("Unknown ReportResolution: $sn")
    }

    val isDismissal: Boolean get() = this == NO_VIOLATION || this == DUPLICATE
}

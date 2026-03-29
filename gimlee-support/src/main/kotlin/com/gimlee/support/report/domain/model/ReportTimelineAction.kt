package com.gimlee.support.report.domain.model

enum class ReportTimelineAction(val shortName: String) {
    CREATED("CRE"),
    ASSIGNED("ASN"),
    STATUS_CHANGED("SC"),
    NOTE_ADDED("NA"),
    RESOLVED("RES");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): ReportTimelineAction =
            map[sn] ?: throw IllegalArgumentException("Unknown ReportTimelineAction: $sn")
    }
}

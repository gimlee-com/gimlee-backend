package com.gimlee.support.report.domain.model

enum class ReportReason(val shortName: String) {
    SPAM("SP"),
    FRAUD("FR"),
    INAPPROPRIATE_CONTENT("IC"),
    COUNTERFEIT("CF"),
    HARASSMENT("HR"),
    COPYRIGHT("CR"),
    WRONG_CATEGORY("WC"),
    OTHER("OT");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): ReportReason =
            map[sn] ?: throw IllegalArgumentException("Unknown ReportReason: $sn")
    }
}

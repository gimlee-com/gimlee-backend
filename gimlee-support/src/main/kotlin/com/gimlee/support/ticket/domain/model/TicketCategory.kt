package com.gimlee.support.ticket.domain.model

enum class TicketCategory(val shortName: String) {
    ACCOUNT_ISSUE("AI"),
    PAYMENT_PROBLEM("PP"),
    ORDER_DISPUTE("OD"),
    TECHNICAL_BUG("TB"),
    FEATURE_REQUEST("FR"),
    SAFETY_CONCERN("SC"),
    OTHER("OT");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): TicketCategory =
            map[sn] ?: throw IllegalArgumentException("Unknown TicketCategory: $sn")
    }
}

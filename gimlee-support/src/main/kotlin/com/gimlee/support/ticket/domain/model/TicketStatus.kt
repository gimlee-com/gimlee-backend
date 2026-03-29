package com.gimlee.support.ticket.domain.model

enum class TicketStatus(val shortName: String) {
    OPEN("O"),
    IN_PROGRESS("IP"),
    AWAITING_USER("AU"),
    RESOLVED("R"),
    CLOSED("C");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): TicketStatus =
            map[sn] ?: throw IllegalArgumentException("Unknown TicketStatus: $sn")
    }
}

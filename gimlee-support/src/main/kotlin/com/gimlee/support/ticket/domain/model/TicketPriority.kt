package com.gimlee.support.ticket.domain.model

enum class TicketPriority(val shortName: String, val sortOrder: Int) {
    LOW("L", 0),
    MEDIUM("M", 1),
    HIGH("H", 2),
    URGENT("U", 3);

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): TicketPriority =
            map[sn] ?: throw IllegalArgumentException("Unknown TicketPriority: $sn")
    }
}

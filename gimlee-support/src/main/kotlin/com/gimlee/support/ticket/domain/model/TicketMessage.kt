package com.gimlee.support.ticket.domain.model

data class TicketMessage(
    val id: String,
    val ticketId: String,
    val authorId: String,
    val authorRole: TicketMessageRole,
    val body: String,
    val createdAt: Long
)

enum class TicketMessageRole(val shortName: String) {
    USER("U"),
    SUPPORT("S");

    companion object {
        private val map = entries.associateBy { it.shortName }
        fun fromShortName(sn: String): TicketMessageRole =
            map[sn] ?: throw IllegalArgumentException("Unknown TicketMessageRole: $sn")
    }
}

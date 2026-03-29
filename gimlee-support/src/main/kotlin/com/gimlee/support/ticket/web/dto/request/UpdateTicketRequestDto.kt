package com.gimlee.support.ticket.web.dto.request

import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus

data class UpdateTicketRequestDto(
    val status: TicketStatus? = null,
    val priority: TicketPriority? = null,
    val assigneeUserId: String? = null
)

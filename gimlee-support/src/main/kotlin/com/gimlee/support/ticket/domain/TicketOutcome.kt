package com.gimlee.support.ticket.domain

import com.gimlee.common.domain.model.Outcome

enum class TicketOutcome(override val httpCode: Int) : Outcome {
    TICKET_CREATED(201),
    TICKET_REPLY_SENT(201),
    TICKET_NOT_FOUND(404),
    TICKET_ACCESS_DENIED(403),
    TICKET_CLOSED_NO_REPLY(400),
    TICKET_UPDATED(200),
    TICKET_ASSIGNEE_NOT_FOUND(404),
    TICKET_INVALID_STATUS_TRANSITION(400);

    override val code: String get() = "SUPPORT_$name"
    override val messageKey: String get() = "status.support.${name.lowercase().replace('_', '-')}"
}

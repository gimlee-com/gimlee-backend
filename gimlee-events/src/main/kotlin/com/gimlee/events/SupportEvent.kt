package com.gimlee.events

import java.time.Instant

// Report events

data class ReportSubmittedEvent(
    val reportId: String,
    val targetId: String,
    val targetType: String,
    val contextId: String?,
    val reporterId: String,
    val reason: String,
    val timestamp: Instant = Instant.now()
)

data class ReportResolvedEvent(
    val reportId: String,
    val targetId: String,
    val targetType: String,
    val resolution: String,
    val resolvedBy: String,
    val timestamp: Instant = Instant.now()
)

data class ReportAssignedEvent(
    val reportId: String,
    val assigneeId: String,
    val assignedBy: String,
    val timestamp: Instant = Instant.now()
)

// Ticket events

data class TicketCreatedEvent(
    val ticketId: String,
    val creatorId: String,
    val category: String,
    val timestamp: Instant = Instant.now()
)

data class TicketReplyEvent(
    val ticketId: String,
    val messageId: String,
    val authorId: String,
    val authorRole: String,
    val timestamp: Instant = Instant.now()
)

data class TicketUpdatedEvent(
    val ticketId: String,
    val updatedBy: String,
    val changes: Map<String, String>,
    val timestamp: Instant = Instant.now()
)

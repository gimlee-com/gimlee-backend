package com.gimlee.support.ticket.domain.model

data class Ticket(
    val id: String,
    val subject: String,
    val category: TicketCategory,
    val status: TicketStatus,
    val priority: TicketPriority,
    val creatorId: String,
    val assigneeId: String?,
    val relatedReportIds: List<String>,
    val messageCount: Int,
    val lastMessageAt: Long?,
    val resolvedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

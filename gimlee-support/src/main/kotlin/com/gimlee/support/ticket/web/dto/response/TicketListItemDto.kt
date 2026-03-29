package com.gimlee.support.ticket.web.dto.response

import com.gimlee.support.ticket.domain.model.TicketCategory
import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ticket summary for list view")
data class TicketListItemDto(
    @Schema(description = "Ticket ID") val id: String,
    @Schema(description = "Ticket subject") val subject: String,
    @Schema(description = "Ticket category") val category: TicketCategory,
    @Schema(description = "Ticket status") val status: TicketStatus,
    @Schema(description = "Ticket priority") val priority: TicketPriority,
    @Schema(description = "Creator username") val creatorUsername: String?,
    @Schema(description = "Creator user ID") val creatorUserId: String,
    @Schema(description = "Assignee username") val assigneeUsername: String?,
    @Schema(description = "Assignee user ID") val assigneeUserId: String?,
    @Schema(description = "Last message timestamp (epoch micros)") val lastMessageAt: Long?,
    @Schema(description = "Number of messages") val messageCount: Int,
    @Schema(description = "Created timestamp (epoch micros)") val createdAt: Long,
    @Schema(description = "Updated timestamp (epoch micros)") val updatedAt: Long
)

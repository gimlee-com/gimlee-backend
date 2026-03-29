package com.gimlee.support.ticket.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ticket conversation message")
data class TicketMessageDto(
    @Schema(description = "Message ID") val id: String,
    @Schema(description = "Author username") val authorUsername: String?,
    @Schema(description = "Author user ID") val authorUserId: String,
    @Schema(description = "Author role (USER or SUPPORT)") val authorRole: String,
    @Schema(description = "Message content (markdown)") val body: String,
    @Schema(description = "Timestamp (epoch micros)") val createdAt: Long
)

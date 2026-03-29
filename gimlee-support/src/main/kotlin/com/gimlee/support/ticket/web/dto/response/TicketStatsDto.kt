package com.gimlee.support.ticket.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Ticket dashboard statistics")
data class TicketStatsDto(
    @Schema(description = "Number of open tickets") val open: Long,
    @Schema(description = "Number of tickets in progress") val inProgress: Long,
    @Schema(description = "Number of tickets awaiting user reply") val awaitingUser: Long,
    @Schema(description = "Tickets resolved today") val resolvedToday: Long,
    @Schema(description = "Average resolution time in microseconds") val averageResolutionTimeMicros: Long?
)

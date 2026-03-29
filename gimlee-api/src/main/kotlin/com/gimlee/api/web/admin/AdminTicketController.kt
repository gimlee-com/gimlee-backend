package com.gimlee.api.web.admin

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.support.ticket.domain.TicketAdminService
import com.gimlee.support.ticket.domain.model.TicketCategory
import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus
import com.gimlee.support.ticket.web.dto.request.ReplyToTicketRequestDto
import com.gimlee.support.ticket.web.dto.request.UpdateTicketRequestDto
import com.gimlee.support.ticket.web.dto.response.TicketDetailDto
import com.gimlee.support.ticket.web.dto.response.TicketListItemDto
import com.gimlee.support.ticket.web.dto.response.TicketStatsDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin Tickets", description = "Admin endpoints for support ticket management")
@RestController
@RequestMapping("/admin/tickets")
class AdminTicketController(
    private val adminTicketService: AdminTicketService,
    private val ticketAdminService: TicketAdminService,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "List tickets",
        description = "Fetches a paginated list of support tickets with optional filtering by status, priority, category, and assignee."
    )
    @ApiResponse(responseCode = "200", description = "Paginated ticket list")
    @GetMapping
    @Privileged(role = "SUPPORT")
    fun listTickets(
        @Parameter(description = "Filter by ticket status") @RequestParam(required = false) status: TicketStatus?,
        @Parameter(description = "Filter by priority") @RequestParam(required = false) priority: TicketPriority?,
        @Parameter(description = "Filter by category") @RequestParam(required = false) category: TicketCategory?,
        @Parameter(description = "Filter by assignee user ID") @RequestParam(required = false) assigneeId: String?,
        @Parameter(description = "Search by subject") @RequestParam(required = false) search: String?,
        @Parameter(description = "Sort field (createdAt, updatedAt, priority, lastMessageAt)") @RequestParam(required = false) sort: String?,
        @Parameter(description = "Sort direction (ASC, DESC)") @RequestParam(required = false, defaultValue = "DESC") direction: String?,
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "30") size: Int
    ): Page<TicketListItemDto> {
        return adminTicketService.listTickets(status, priority, category, assigneeId, search, sort, direction, page, size)
    }

    @Operation(
        summary = "Get ticket detail",
        description = "Fetches detailed information about a specific ticket including its full conversation history."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Ticket detail with messages",
        content = [Content(schema = Schema(implementation = TicketDetailDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ticket not found",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/{ticketId}")
    @Privileged(role = "SUPPORT")
    fun getTicketDetail(
        @Parameter(description = "Ticket ID") @PathVariable ticketId: String
    ): ResponseEntity<Any> {
        val (outcome, data) = adminTicketService.getTicketDetail(ticketId)
        return handleOutcome(outcome, data)
    }

    @Operation(
        summary = "Update ticket",
        description = "Updates a ticket's status, priority, or assignee. All fields are optional — only provided fields are updated."
    )
    @ApiResponse(responseCode = "200", description = "Ticket updated", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "Ticket not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PatchMapping("/{ticketId}")
    @Privileged(role = "SUPPORT")
    fun updateTicket(
        @Parameter(description = "Ticket ID") @PathVariable ticketId: String,
        @Valid @RequestBody request: UpdateTicketRequestDto
    ): ResponseEntity<Any> {
        val performedBy = HttpServletRequestAuthUtil.getPrincipal().userId
        val outcome = ticketAdminService.updateTicket(ticketId, request.status, request.priority, request.assigneeUserId, performedBy)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Reply to ticket as support",
        description = "Adds a support reply to a ticket. Automatically transitions IN_PROGRESS/OPEN tickets to AWAITING_USER."
    )
    @ApiResponse(responseCode = "201", description = "Reply sent", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "400", description = "Ticket is closed", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "Ticket not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PostMapping("/{ticketId}/reply")
    @Privileged(role = "SUPPORT")
    fun replyToTicket(
        @Parameter(description = "Ticket ID") @PathVariable ticketId: String,
        @Valid @RequestBody request: ReplyToTicketRequestDto
    ): ResponseEntity<Any> {
        val supportUserId = HttpServletRequestAuthUtil.getPrincipal().userId
        val (outcome, _) = ticketAdminService.addSupportReply(ticketId, supportUserId, request.body)
        return handleOutcome(outcome)
    }

    @Operation(
        summary = "Get ticket statistics",
        description = "Returns dashboard statistics: open tickets, in-progress, awaiting user, resolved today, and average resolution time."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Ticket statistics",
        content = [Content(schema = Schema(implementation = TicketStatsDto::class))]
    )
    @GetMapping("/stats")
    @Privileged(role = "SUPPORT")
    fun getStats(): TicketStatsDto {
        return ticketAdminService.getStats()
    }
}

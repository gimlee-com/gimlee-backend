package com.gimlee.api.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.service.UserService
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.support.ticket.domain.TicketService
import com.gimlee.support.ticket.web.dto.request.CreateTicketRequestDto
import com.gimlee.support.ticket.web.dto.request.ReplyToTicketRequestDto
import com.gimlee.support.ticket.web.dto.response.TicketDetailDto
import com.gimlee.support.ticket.web.dto.response.TicketListItemDto
import com.gimlee.support.ticket.web.dto.response.TicketMessageDto
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Tickets", description = "User-facing support ticket endpoints")
@RestController
@RequestMapping("/tickets")
class TicketController(
    private val ticketService: TicketService,
    private val userService: UserService,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "Create ticket",
        description = "Creates a new support ticket with the given subject, category, and initial message body."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Ticket created. Possible status codes: SUPPORT_TICKET_CREATED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping
    @Privileged(role = "USER")
    fun createTicket(@Valid @RequestBody request: CreateTicketRequestDto): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val (outcome, ticket) = ticketService.createTicket(principal.userId, request.subject, request.category, request.body)
        return handleOutcome(outcome, ticket)
    }

    @Operation(
        summary = "My tickets",
        description = "Fetches a paginated list of support tickets created by the authenticated user."
    )
    @ApiResponse(responseCode = "200", description = "Paginated list of user's tickets")
    @GetMapping("/mine")
    @Privileged(role = "USER")
    fun myTickets(
        @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): Page<TicketListItemDto> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val ticketsPage = ticketService.listUserTickets(principal.userId, page, size)

        val userIds = mutableSetOf<String>()
        ticketsPage.content.forEach { ticket ->
            userIds.add(ticket.creatorId)
            ticket.assigneeId?.let { userIds.add(it) }
        }
        val usernames = userService.findUsernamesByIds(userIds.toList())

        return ticketsPage.map { ticket ->
            TicketListItemDto(
                id = ticket.id,
                subject = ticket.subject,
                category = ticket.category,
                status = ticket.status,
                priority = ticket.priority,
                creatorUsername = usernames[ticket.creatorId],
                creatorUserId = ticket.creatorId,
                assigneeUsername = ticket.assigneeId?.let { usernames[it] },
                assigneeUserId = ticket.assigneeId,
                lastMessageAt = ticket.lastMessageAt,
                messageCount = ticket.messageCount,
                createdAt = ticket.createdAt,
                updatedAt = ticket.updatedAt
            )
        }
    }

    @Operation(
        summary = "Get ticket detail",
        description = "Fetches full details and conversation for a ticket owned by the authenticated user."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Ticket detail with messages",
        content = [Content(schema = Schema(implementation = TicketDetailDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Access denied — not your ticket",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Ticket not found",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/{ticketId}")
    @Privileged(role = "USER")
    fun getTicket(
        @Parameter(description = "Ticket ID") @PathVariable ticketId: String
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val (outcome, ticket) = ticketService.getTicketForUser(ticketId, principal.userId)
        if (ticket == null) return handleOutcome(outcome)

        val messages = ticketService.getTicketMessages(ticketId)
        val userIds = mutableSetOf(ticket.creatorId)
        ticket.assigneeId?.let { userIds.add(it) }
        messages.forEach { userIds.add(it.authorId) }
        val usernames = userService.findUsernamesByIds(userIds.toList())

        val dto = TicketDetailDto(
            id = ticket.id,
            subject = ticket.subject,
            category = ticket.category,
            status = ticket.status,
            priority = ticket.priority,
            creatorUsername = usernames[ticket.creatorId],
            creatorUserId = ticket.creatorId,
            assigneeUsername = ticket.assigneeId?.let { usernames[it] },
            assigneeUserId = ticket.assigneeId,
            lastMessageAt = ticket.lastMessageAt,
            messageCount = ticket.messageCount,
            createdAt = ticket.createdAt,
            updatedAt = ticket.updatedAt,
            messages = messages.map { msg ->
                TicketMessageDto(
                    id = msg.id,
                    authorUsername = usernames[msg.authorId],
                    authorUserId = msg.authorId,
                    authorRole = msg.authorRole.name,
                    body = msg.body,
                    createdAt = msg.createdAt
                )
            },
            relatedReportIds = ticket.relatedReportIds
        )

        return handleOutcome(CommonOutcome.SUCCESS, dto)
    }

    @Operation(
        summary = "Reply to ticket",
        description = "Adds a user reply to their own ticket. Automatically transitions AWAITING_USER tickets back to IN_PROGRESS."
    )
    @ApiResponse(responseCode = "201", description = "Reply sent", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "400", description = "Ticket is closed", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "403", description = "Access denied — not your ticket", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @ApiResponse(responseCode = "404", description = "Ticket not found", content = [Content(schema = Schema(implementation = StatusResponseDto::class))])
    @PostMapping("/{ticketId}/reply")
    @Privileged(role = "USER")
    fun replyToTicket(
        @Parameter(description = "Ticket ID") @PathVariable ticketId: String,
        @Valid @RequestBody request: ReplyToTicketRequestDto
    ): ResponseEntity<Any> {
        val principal = HttpServletRequestAuthUtil.getPrincipal()
        val (outcome, _) = ticketService.addUserReply(ticketId, principal.userId, request.body)
        return handleOutcome(outcome)
    }
}

package com.gimlee.support.ticket.domain

import com.gimlee.common.toMicros
import com.gimlee.events.TicketReplyEvent
import com.gimlee.events.TicketUpdatedEvent
import com.gimlee.support.ticket.domain.model.TicketMessageRole
import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus
import com.gimlee.support.ticket.persistence.TicketMessageRepository
import com.gimlee.support.ticket.persistence.TicketRepository
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument
import com.gimlee.support.ticket.web.dto.response.TicketStatsDto
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class TicketAdminService(
    private val ticketRepository: TicketRepository,
    private val messageRepository: TicketMessageRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val validTransitions = mapOf(
        TicketStatus.OPEN to setOf(TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_USER, TicketStatus.RESOLVED, TicketStatus.CLOSED),
        TicketStatus.IN_PROGRESS to setOf(TicketStatus.OPEN, TicketStatus.AWAITING_USER, TicketStatus.RESOLVED, TicketStatus.CLOSED),
        TicketStatus.AWAITING_USER to setOf(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
        TicketStatus.RESOLVED to setOf(TicketStatus.OPEN, TicketStatus.CLOSED),
        TicketStatus.CLOSED to setOf(TicketStatus.OPEN)
    )

    fun addSupportReply(
        ticketId: String,
        supportUserId: String,
        body: String
    ): Pair<TicketOutcome, com.gimlee.support.ticket.domain.model.TicketMessage?> {
        val ticket = ticketRepository.findById(ObjectId(ticketId))
            ?: return Pair(TicketOutcome.TICKET_NOT_FOUND, null)

        val status = TicketStatus.fromShortName(ticket.status)
        if (status == TicketStatus.CLOSED) {
            return Pair(TicketOutcome.TICKET_CLOSED_NO_REPLY, null)
        }

        val now = Instant.now().toMicros()
        val msgDoc = messageRepository.save(
            TicketMessageDocument(
                ticketId = ticket.id!!,
                authorId = ObjectId(supportUserId),
                authorRole = TicketMessageRole.SUPPORT.shortName,
                body = body,
                createdAt = now
            )
        )

        ticketRepository.incrementMessageCount(ticket.id, now, now)

        if (status == TicketStatus.OPEN || status == TicketStatus.IN_PROGRESS) {
            ticketRepository.update(
                id = ticket.id,
                status = TicketStatus.AWAITING_USER,
                priority = null,
                priorityOrder = null,
                assigneeId = null,
                resolvedAt = null,
                updatedAt = now
            )
        }

        eventPublisher.publishEvent(
            TicketReplyEvent(
                ticketId = ticketId,
                ticketCreatorId = ticket.creatorId.toHexString(),
                messageId = msgDoc.id!!.toHexString(),
                authorId = supportUserId,
                authorRole = TicketMessageRole.SUPPORT.name
            )
        )

        return Pair(TicketOutcome.TICKET_REPLY_SENT, msgDoc.toDomain())
    }

    fun updateTicket(
        ticketId: String,
        status: TicketStatus?,
        priority: TicketPriority?,
        assigneeId: String?,
        performedBy: String
    ): TicketOutcome {
        val ticket = ticketRepository.findById(ObjectId(ticketId))
            ?: return TicketOutcome.TICKET_NOT_FOUND

        if (status != null) {
            val currentStatus = TicketStatus.fromShortName(ticket.status)
            val allowed = validTransitions[currentStatus] ?: emptySet()
            if (status !in allowed) {
                return TicketOutcome.TICKET_INVALID_STATUS_TRANSITION
            }
        }

        val now = Instant.now().toMicros()
        val resolvedAt = if (status == TicketStatus.RESOLVED) now else null

        ticketRepository.update(
            id = ticket.id!!,
            status = status,
            priority = priority,
            priorityOrder = priority?.sortOrder,
            assigneeId = assigneeId?.let { ObjectId(it) },
            resolvedAt = resolvedAt,
            updatedAt = now
        )

        val changes = mutableMapOf<String, String>()
        status?.let { changes["status"] = it.name }
        priority?.let { changes["priority"] = it.name }
        assigneeId?.let { changes["assigneeId"] = it }

        eventPublisher.publishEvent(
            TicketUpdatedEvent(
                ticketId = ticketId,
                ticketCreatorId = ticket.creatorId.toHexString(),
                updatedBy = performedBy,
                changes = changes
            )
        )

        return TicketOutcome.TICKET_UPDATED
    }

    fun getStats(): TicketStatsDto {
        val todayStart = LocalDate.now(ZoneOffset.UTC)
            .atStartOfDay().toInstant(ZoneOffset.UTC).toMicros()
        return TicketStatsDto(
            open = ticketRepository.countByStatus(TicketStatus.OPEN),
            inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS),
            awaitingUser = ticketRepository.countByStatus(TicketStatus.AWAITING_USER),
            resolvedToday = ticketRepository.countResolvedSince(todayStart),
            averageResolutionTimeMicros = ticketRepository.averageResolutionTimeMicros()
        )
    }
}

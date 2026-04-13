package com.gimlee.support.ticket.domain

import com.gimlee.common.toMicros
import com.gimlee.events.TicketCreatedEvent
import com.gimlee.events.TicketReplyEvent
import com.gimlee.support.ticket.domain.model.Ticket
import com.gimlee.support.ticket.domain.model.TicketCategory
import com.gimlee.support.ticket.domain.model.TicketMessage
import com.gimlee.support.ticket.domain.model.TicketMessageRole
import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus
import com.gimlee.support.ticket.persistence.TicketMessageRepository
import com.gimlee.support.ticket.persistence.TicketRepository
import com.gimlee.support.ticket.persistence.model.TicketDocument
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TicketService(
    private val ticketRepository: TicketRepository,
    private val messageRepository: TicketMessageRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    fun createTicket(
        creatorId: String,
        subject: String,
        category: TicketCategory,
        body: String
    ): Pair<TicketOutcome, Ticket?> {
        val now = Instant.now().toMicros()
        val ticketDoc = ticketRepository.save(
            TicketDocument(
                subject = subject,
                category = category.shortName,
                status = TicketStatus.OPEN.shortName,
                priority = TicketPriority.MEDIUM.shortName,
                priorityOrder = TicketPriority.MEDIUM.sortOrder,
                creatorId = ObjectId(creatorId),
                assigneeId = null,
                relatedReportIds = emptyList(),
                messageCount = 1,
                lastMessageAt = now,
                resolvedAt = null,
                createdAt = now,
                updatedAt = now
            )
        )

        messageRepository.save(
            TicketMessageDocument(
                ticketId = ticketDoc.id!!,
                authorId = ObjectId(creatorId),
                authorRole = TicketMessageRole.USER.shortName,
                body = body,
                createdAt = now
            )
        )

        eventPublisher.publishEvent(
            TicketCreatedEvent(
                ticketId = ticketDoc.id.toHexString(),
                creatorId = creatorId,
                category = category.name
            )
        )

        return Pair(TicketOutcome.TICKET_CREATED, ticketDoc.toDomain())
    }

    fun addUserReply(
        ticketId: String,
        userId: String,
        body: String
    ): Pair<TicketOutcome, TicketMessage?> {
        val ticket = ticketRepository.findById(ObjectId(ticketId))
            ?: return Pair(TicketOutcome.TICKET_NOT_FOUND, null)

        if (ticket.creatorId.toHexString() != userId) {
            return Pair(TicketOutcome.TICKET_ACCESS_DENIED, null)
        }

        val status = TicketStatus.fromShortName(ticket.status)
        if (status == TicketStatus.CLOSED) {
            return Pair(TicketOutcome.TICKET_CLOSED_NO_REPLY, null)
        }

        val now = Instant.now().toMicros()
        val msgDoc = messageRepository.save(
            TicketMessageDocument(
                ticketId = ticket.id!!,
                authorId = ObjectId(userId),
                authorRole = TicketMessageRole.USER.shortName,
                body = body,
                createdAt = now
            )
        )

        ticketRepository.incrementMessageCount(ticket.id, now, now)

        if (status == TicketStatus.AWAITING_USER) {
            ticketRepository.update(
                id = ticket.id,
                status = TicketStatus.IN_PROGRESS,
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
                authorId = userId,
                authorRole = TicketMessageRole.USER.name
            )
        )

        return Pair(TicketOutcome.TICKET_REPLY_SENT, msgDoc.toDomain())
    }

    fun getTicketForUser(ticketId: String, userId: String): Pair<TicketOutcome, Ticket?> {
        val ticket = ticketRepository.findById(ObjectId(ticketId))
            ?: return Pair(TicketOutcome.TICKET_NOT_FOUND, null)

        if (ticket.creatorId.toHexString() != userId) {
            return Pair(TicketOutcome.TICKET_ACCESS_DENIED, null)
        }

        return Pair(TicketOutcome.TICKET_CREATED, ticket.toDomain())
    }

    fun listUserTickets(userId: String, page: Int, size: Int): Page<Ticket> {
        val pageable = PageRequest.of(page, size)
        return ticketRepository.findByCreatorIdPaginated(ObjectId(userId), pageable).map { it.toDomain() }
    }

    fun getTicketMessages(ticketId: String): List<TicketMessage> {
        return messageRepository.findByTicketId(ObjectId(ticketId)).map { it.toDomain() }
    }
}

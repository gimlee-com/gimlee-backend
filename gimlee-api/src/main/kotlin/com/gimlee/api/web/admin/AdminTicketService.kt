package com.gimlee.api.web.admin

import com.gimlee.auth.service.UserService
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.support.ticket.domain.TicketOutcome
import com.gimlee.support.ticket.domain.TicketService
import com.gimlee.support.ticket.domain.model.TicketCategory
import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus
import com.gimlee.support.ticket.persistence.TicketRepository
import com.gimlee.support.ticket.web.dto.response.TicketDetailDto
import com.gimlee.support.ticket.web.dto.response.TicketListItemDto
import com.gimlee.support.ticket.web.dto.response.TicketMessageDto
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class AdminTicketService(
    private val ticketService: TicketService,
    private val ticketRepository: TicketRepository,
    private val userService: UserService
) {

    fun listTickets(
        status: TicketStatus?, priority: TicketPriority?,
        category: TicketCategory?, assigneeId: String?,
        search: String?, sort: String?, direction: String?,
        page: Int, size: Int
    ): Page<TicketListItemDto> {
        val pageable = PageRequest.of(page, size)
        val assigneeObjectId = assigneeId?.let { ObjectId(it) }
        val ticketsPage = ticketRepository.findAllPaginated(
            status, priority, category, assigneeObjectId, search, sort, direction, pageable
        )

        val userIds = mutableSetOf<String>()
        ticketsPage.content.forEach { doc ->
            userIds.add(doc.creatorId.toHexString())
            doc.assigneeId?.let { userIds.add(it.toHexString()) }
        }
        val usernames = userService.findUsernamesByIds(userIds.toList())

        return ticketsPage.map { doc ->
            val ticket = doc.toDomain()
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

    fun getTicketDetail(ticketId: String): Pair<Outcome, TicketDetailDto?> {
        val doc = ticketRepository.findById(ObjectId(ticketId))
            ?: return Pair(TicketOutcome.TICKET_NOT_FOUND, null)

        val ticket = doc.toDomain()
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

        return Pair(CommonOutcome.SUCCESS, dto)
    }
}

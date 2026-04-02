package com.gimlee.support.ticket.domain

import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus
import com.gimlee.support.ticket.persistence.TicketMessageRepository
import com.gimlee.support.ticket.persistence.TicketRepository
import com.gimlee.support.ticket.persistence.model.TicketDocument
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher

class TicketAdminServiceTest : FunSpec({

    val ticketRepository = mockk<TicketRepository>(relaxed = true)
    val messageRepository = mockk<TicketMessageRepository>(relaxed = true)
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    val service = TicketAdminService(ticketRepository, messageRepository, eventPublisher)

    val ticketId = ObjectId()
    val performedBy = ObjectId().toHexString()

    fun ticketDoc(status: TicketStatus) = TicketDocument(
        id = ticketId,
        subject = "Test ticket",
        category = "AI",
        status = status.shortName,
        priority = TicketPriority.MEDIUM.shortName,
        priorityOrder = TicketPriority.MEDIUM.sortOrder,
        creatorId = ObjectId(),
        assigneeId = null,
        relatedReportIds = emptyList(),
        messageCount = 0,
        lastMessageAt = null,
        resolvedAt = null,
        createdAt = 1000000L,
        updatedAt = 1000000L
    )

    beforeTest {
        clearMocks(ticketRepository, messageRepository, eventPublisher)
    }

    // ------------------------------------------------------------------
    // Valid transitions
    // ------------------------------------------------------------------
    data class TransitionCase(val from: TicketStatus, val to: TicketStatus)

    val validTransitions = listOf(
        TransitionCase(TicketStatus.OPEN, TicketStatus.IN_PROGRESS),
        TransitionCase(TicketStatus.OPEN, TicketStatus.AWAITING_USER),
        TransitionCase(TicketStatus.OPEN, TicketStatus.RESOLVED),
        TransitionCase(TicketStatus.OPEN, TicketStatus.CLOSED),
        TransitionCase(TicketStatus.IN_PROGRESS, TicketStatus.OPEN),
        TransitionCase(TicketStatus.IN_PROGRESS, TicketStatus.AWAITING_USER),
        TransitionCase(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
        TransitionCase(TicketStatus.IN_PROGRESS, TicketStatus.CLOSED),
        TransitionCase(TicketStatus.AWAITING_USER, TicketStatus.OPEN),
        TransitionCase(TicketStatus.AWAITING_USER, TicketStatus.IN_PROGRESS),
        TransitionCase(TicketStatus.AWAITING_USER, TicketStatus.RESOLVED),
        TransitionCase(TicketStatus.AWAITING_USER, TicketStatus.CLOSED),
        TransitionCase(TicketStatus.RESOLVED, TicketStatus.OPEN),
        TransitionCase(TicketStatus.RESOLVED, TicketStatus.CLOSED),
        TransitionCase(TicketStatus.CLOSED, TicketStatus.OPEN),
    )

    context("valid status transitions are accepted") {
        validTransitions.forEach { (from, to) ->
            test("${from.name} → ${to.name} is allowed") {
                every { ticketRepository.findById(ticketId) } returns ticketDoc(from)

                val outcome = service.updateTicket(ticketId.toHexString(), to, null, null, performedBy)

                outcome shouldBe TicketOutcome.TICKET_UPDATED
                verify { ticketRepository.update(ticketId, to, null, null, null, any(), any()) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Invalid transitions
    // ------------------------------------------------------------------
    val invalidTransitions = listOf(
        TransitionCase(TicketStatus.OPEN, TicketStatus.OPEN),
        TransitionCase(TicketStatus.CLOSED, TicketStatus.CLOSED),
        TransitionCase(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS),
        TransitionCase(TicketStatus.RESOLVED, TicketStatus.AWAITING_USER),
        TransitionCase(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS),
        TransitionCase(TicketStatus.CLOSED, TicketStatus.AWAITING_USER),
        TransitionCase(TicketStatus.CLOSED, TicketStatus.RESOLVED),
    )

    context("invalid status transitions are rejected") {
        invalidTransitions.forEach { (from, to) ->
            test("${from.name} → ${to.name} is rejected") {
                every { ticketRepository.findById(ticketId) } returns ticketDoc(from)

                val outcome = service.updateTicket(ticketId.toHexString(), to, null, null, performedBy)

                outcome shouldBe TicketOutcome.TICKET_INVALID_STATUS_TRANSITION
                verify(exactly = 0) { ticketRepository.update(any(), any(), any(), any(), any(), any(), any()) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------
    test("update with null status skips transition validation") {
        every { ticketRepository.findById(ticketId) } returns ticketDoc(TicketStatus.CLOSED)

        val outcome = service.updateTicket(
            ticketId.toHexString(), null, TicketPriority.HIGH, null, performedBy
        )

        outcome shouldBe TicketOutcome.TICKET_UPDATED
        verify {
            ticketRepository.update(
                ticketId, null, TicketPriority.HIGH, TicketPriority.HIGH.sortOrder, null, null, any()
            )
        }
    }

    test("update for non-existent ticket returns NOT_FOUND") {
        every { ticketRepository.findById(any()) } returns null

        val outcome = service.updateTicket(
            ObjectId().toHexString(), TicketStatus.IN_PROGRESS, null, null, performedBy
        )

        outcome shouldBe TicketOutcome.TICKET_NOT_FOUND
    }

    test("resolving a ticket sets resolvedAt timestamp") {
        every { ticketRepository.findById(ticketId) } returns ticketDoc(TicketStatus.IN_PROGRESS)

        val outcome = service.updateTicket(
            ticketId.toHexString(), TicketStatus.RESOLVED, null, null, performedBy
        )

        outcome shouldBe TicketOutcome.TICKET_UPDATED
        verify {
            ticketRepository.update(
                ticketId, TicketStatus.RESOLVED, null, null, null, match { it != null && it > 0 }, any()
            )
        }
    }

    // ------------------------------------------------------------------
    // addSupportReply transition behavior
    // ------------------------------------------------------------------
    test("support reply on OPEN ticket transitions to AWAITING_USER") {
        val doc = ticketDoc(TicketStatus.OPEN)
        every { ticketRepository.findById(ticketId) } returns doc
        every { messageRepository.save(any()) } answers {
            firstArg<TicketMessageDocument>().copy(id = ObjectId())
        }

        val (outcome, _) = service.addSupportReply(ticketId.toHexString(), performedBy, "reply body")

        outcome shouldBe TicketOutcome.TICKET_REPLY_SENT
        verify {
            ticketRepository.update(
                ticketId, TicketStatus.AWAITING_USER, null, null, null, null, any()
            )
        }
    }

    test("support reply on IN_PROGRESS ticket transitions to AWAITING_USER") {
        val doc = ticketDoc(TicketStatus.IN_PROGRESS)
        every { ticketRepository.findById(ticketId) } returns doc
        every { messageRepository.save(any()) } answers {
            firstArg<TicketMessageDocument>().copy(id = ObjectId())
        }

        val (outcome, _) = service.addSupportReply(ticketId.toHexString(), performedBy, "reply body")

        outcome shouldBe TicketOutcome.TICKET_REPLY_SENT
        verify {
            ticketRepository.update(
                ticketId, TicketStatus.AWAITING_USER, null, null, null, null, any()
            )
        }
    }

    test("support reply on AWAITING_USER ticket does NOT change status") {
        val doc = ticketDoc(TicketStatus.AWAITING_USER)
        every { ticketRepository.findById(ticketId) } returns doc
        every { messageRepository.save(any()) } answers {
            firstArg<TicketMessageDocument>().copy(id = ObjectId())
        }

        val (outcome, _) = service.addSupportReply(ticketId.toHexString(), performedBy, "follow-up")

        outcome shouldBe TicketOutcome.TICKET_REPLY_SENT
        verify(exactly = 0) {
            ticketRepository.update(any(), any(), any(), any(), any(), any(), any())
        }
    }

    test("support reply on CLOSED ticket is rejected") {
        every { ticketRepository.findById(ticketId) } returns ticketDoc(TicketStatus.CLOSED)

        val (outcome, msg) = service.addSupportReply(ticketId.toHexString(), performedBy, "nope")

        outcome shouldBe TicketOutcome.TICKET_CLOSED_NO_REPLY
        msg shouldBe null
    }
})

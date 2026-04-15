package com.gimlee.notifications.event

import com.gimlee.events.TicketReplyEvent
import com.gimlee.events.TicketUpdatedEvent
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class TicketNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handleTicketReply(event: TicketReplyEvent) {
        try {
            if (event.authorRole != "SUPPORT") return

            val creatorId = event.ticketCreatorId
            notificationService.createNotification(
                userId = creatorId,
                type = NotificationType.TICKET_REPLY,
                language = languageProvider.getLanguage(creatorId),
                suggestedAction = SuggestedAction(SuggestedActionType.TICKET_DETAILS, event.ticketId),
                metadata = mapOf("ticketId" to event.ticketId)
            )
        } catch (e: Exception) {
            log.error("Failed to process ticket reply notification: ticketId={}", event.ticketId, e)
        }
    }

    @Async
    @EventListener
    fun handleTicketUpdated(event: TicketUpdatedEvent) {
        try {
            val newStatus = event.changes["status"] ?: return
            val creatorId = event.ticketCreatorId

            when (newStatus) {
                "AWAITING_USER" -> notificationService.createNotification(
                    userId = creatorId,
                    type = NotificationType.TICKET_AWAITING_USER,
                    language = languageProvider.getLanguage(creatorId),
                    suggestedAction = SuggestedAction(SuggestedActionType.TICKET_DETAILS, event.ticketId),
                    metadata = mapOf("ticketId" to event.ticketId)
                )
                else -> notificationService.createNotification(
                    userId = creatorId,
                    type = NotificationType.TICKET_STATUS_CHANGE,
                    language = languageProvider.getLanguage(creatorId),
                    messageArgs = arrayOf(newStatus.lowercase().replace('_', ' ')),
                    suggestedAction = SuggestedAction(SuggestedActionType.TICKET_DETAILS, event.ticketId),
                    metadata = mapOf("ticketId" to event.ticketId, "status" to newStatus)
                )
            }
        } catch (e: Exception) {
            log.error("Failed to process ticket update notification: ticketId={}", event.ticketId, e)
        }
    }
}

package com.gimlee.notifications.event

import com.gimlee.events.ReportAssignedEvent
import com.gimlee.events.ReportSubmittedEvent
import com.gimlee.events.TicketCreatedEvent
import com.gimlee.notifications.domain.AdminUserProvider
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class AdminNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider,
    private val adminUserProvider: AdminUserProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // X1: New report submitted → notify all admins
    @Async
    @EventListener
    fun handleReportSubmitted(event: ReportSubmittedEvent) {
        try {
            val adminIds = adminUserProvider.getAdminUserIds()
            for (adminId in adminIds) {
                notificationService.createNotification(
                    userId = adminId,
                    type = NotificationType.ADMIN_NEW_REPORT,
                    language = languageProvider.getLanguage(adminId),
                    messageArgs = arrayOf(event.targetType, event.reason),
                    suggestedAction = SuggestedAction(SuggestedActionType.ADMIN_REPORT_DETAILS, event.reportId),
                    metadata = mapOf(
                        "reportId" to event.reportId,
                        "targetType" to event.targetType,
                        "targetId" to event.targetId
                    )
                )
            }
        } catch (e: Exception) {
            log.error("Failed to process new report admin notification: reportId={}", event.reportId, e)
        }
    }

    // X2: Report assigned → notify assignee
    @Async
    @EventListener
    fun handleReportAssigned(event: ReportAssignedEvent) {
        try {
            notificationService.createNotification(
                userId = event.assigneeId,
                type = NotificationType.ADMIN_REPORT_ASSIGNED,
                language = languageProvider.getLanguage(event.assigneeId),
                suggestedAction = SuggestedAction(SuggestedActionType.ADMIN_REPORT_DETAILS, event.reportId),
                metadata = mapOf("reportId" to event.reportId)
            )
        } catch (e: Exception) {
            log.error("Failed to process report assigned notification: reportId={}", event.reportId, e)
        }
    }

    // X3: New ticket created → notify all admins
    @Async
    @EventListener
    fun handleTicketCreated(event: TicketCreatedEvent) {
        try {
            val adminIds = adminUserProvider.getAdminUserIds()
            for (adminId in adminIds) {
                notificationService.createNotification(
                    userId = adminId,
                    type = NotificationType.ADMIN_NEW_TICKET,
                    language = languageProvider.getLanguage(adminId),
                    messageArgs = arrayOf(event.category),
                    suggestedAction = SuggestedAction(SuggestedActionType.ADMIN_TICKET_DETAILS, event.ticketId),
                    metadata = mapOf(
                        "ticketId" to event.ticketId,
                        "category" to event.category
                    )
                )
            }
        } catch (e: Exception) {
            log.error("Failed to process new ticket admin notification: ticketId={}", event.ticketId, e)
        }
    }
}

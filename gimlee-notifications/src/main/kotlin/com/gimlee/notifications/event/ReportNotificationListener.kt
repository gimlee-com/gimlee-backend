package com.gimlee.notifications.event

import com.gimlee.events.ReportResolvedEvent
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class ReportNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handleReportResolved(event: ReportResolvedEvent) {
        try {
            notifyReporter(event)
            notifyModerationTarget(event)
        } catch (e: Exception) {
            log.error("Failed to process report resolved notification: reportId={}", event.reportId, e)
        }
    }

    private fun notifyReporter(event: ReportResolvedEvent) {
        val reporterId = event.reporterId
        notificationService.createNotification(
            userId = reporterId,
            type = NotificationType.REPORT_RESOLVED,
            language = languageProvider.getLanguage(reporterId),
            suggestedAction = SuggestedAction(SuggestedActionType.SUPPORT_CENTER),
            metadata = mapOf("reportId" to event.reportId)
        )
    }

    private fun notifyModerationTarget(event: ReportResolvedEvent) {
        when (event.resolution) {
            "USER_WARNED" -> {
                val targetUserId = event.targetId
                notificationService.createNotification(
                    userId = targetUserId,
                    type = NotificationType.MODERATION_WARNING,
                    language = languageProvider.getLanguage(targetUserId),
                    metadata = mapOf("reportId" to event.reportId)
                )
            }
            "CONTENT_REMOVED" -> {
                val targetUserId = event.targetId
                notificationService.createNotification(
                    userId = targetUserId,
                    type = NotificationType.MODERATION_CONTENT_REMOVED,
                    language = languageProvider.getLanguage(targetUserId),
                    metadata = mapOf("reportId" to event.reportId)
                )
            }
        }
    }
}

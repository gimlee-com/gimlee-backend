package com.gimlee.notifications.event

import com.gimlee.events.BanExpiryApproachingEvent
import com.gimlee.events.UserBannedEvent
import com.gimlee.events.UserRegisteredEvent
import com.gimlee.events.UserUnbannedEvent
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class AccountNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handleUserBanned(event: UserBannedEvent) {
        try {
            notificationService.createNotification(
                userId = event.userId,
                type = NotificationType.ACCOUNT_BAN,
                language = languageProvider.getLanguage(event.userId),
                messageArgs = arrayOf(event.reason),
                metadata = mapOf("reason" to event.reason)
            )
        } catch (e: Exception) {
            log.error("Failed to process ban notification: userId={}", event.userId, e)
        }
    }

    @Async
    @EventListener
    fun handleUserUnbanned(event: UserUnbannedEvent) {
        try {
            notificationService.createNotification(
                userId = event.userId,
                type = NotificationType.ACCOUNT_UNBAN,
                language = languageProvider.getLanguage(event.userId)
            )
        } catch (e: Exception) {
            log.error("Failed to process unban notification: userId={}", event.userId, e)
        }
    }

    @Async
    @EventListener
    fun handleUserRegistered(event: UserRegisteredEvent) {
        try {
            notificationService.createNotification(
                userId = event.userId,
                type = NotificationType.ACCOUNT_WELCOME,
                language = languageProvider.getLanguage(event.userId),
                actionUrl = "/getting-started"
            )
        } catch (e: Exception) {
            log.error("Failed to process welcome notification: userId={}", event.userId, e)
        }
    }

    // U3: Ban expiring soon
    @Async
    @EventListener
    fun handleBanExpiryApproaching(event: BanExpiryApproachingEvent) {
        try {
            notificationService.createNotification(
                userId = event.userId,
                type = NotificationType.ACCOUNT_BAN_EXPIRING,
                language = languageProvider.getLanguage(event.userId),
                metadata = mapOf("bannedUntil" to event.bannedUntil.toString())
            )
        } catch (e: Exception) {
            log.error("Failed to process ban expiry approaching notification: userId={}", event.userId, e)
        }
    }
}

package com.gimlee.notifications.event

import com.gimlee.events.AdStatusChangedEvent
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class AdNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handleAdStatusChanged(event: AdStatusChangedEvent) {
        try {
            val sellerId = event.sellerId ?: return
            when (event.reason) {
                AdStatusChangedEvent.Reason.STOCK_DEPLETED -> notifyStockDepleted(event, sellerId)
                AdStatusChangedEvent.Reason.CATEGORY_HIDDEN -> notifyCategoryHidden(event, sellerId)
                else -> {}
            }
        } catch (e: Exception) {
            log.error("Failed to process ad notification: adId={}, reason={}", event.adId, event.reason, e)
        }
    }

    private fun notifyStockDepleted(event: AdStatusChangedEvent, sellerId: String) {
        notificationService.createNotification(
            userId = sellerId,
            type = NotificationType.AD_STOCK_DEPLETED,
            language = languageProvider.getLanguage(sellerId),
            actionUrl = "/seller/ads/${event.adId}",
            metadata = mapOf("adId" to event.adId)
        )
    }

    private fun notifyCategoryHidden(event: AdStatusChangedEvent, sellerId: String) {
        notificationService.createNotification(
            userId = sellerId,
            type = NotificationType.AD_CATEGORY_HIDDEN,
            language = languageProvider.getLanguage(sellerId),
            actionUrl = "/seller/ads/${event.adId}/edit",
            metadata = mapOf("adId" to event.adId)
        )
    }
}

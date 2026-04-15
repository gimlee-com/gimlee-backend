package com.gimlee.notifications.event

import com.gimlee.events.AdPriceChangedEvent
import com.gimlee.events.AdRestockedEvent
import com.gimlee.events.AdStatusChangedEvent
import com.gimlee.notifications.domain.AdWatcherProvider
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class AdNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider,
    private val watcherProvider: AdWatcherProvider
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
                AdStatusChangedEvent.Reason.USER_ACTION -> {
                    if (event.newStatus == "INACTIVE") notifyWatchlistDeactivated(event, sellerId)
                }
                else -> {}
            }
        } catch (e: Exception) {
            log.error("Failed to process ad notification: adId={}, reason={}", event.adId, event.reason, e)
        }
    }

    @Async
    @EventListener
    fun handleAdPriceChanged(event: AdPriceChangedEvent) {
        try {
            notifyWatchersOfPriceChange(event)
        } catch (e: Exception) {
            log.error("Failed to process price change notification: adId={}", event.adId, e)
        }
    }

    @Async
    @EventListener
    fun handleAdRestocked(event: AdRestockedEvent) {
        try {
            notifyWatchersOfRestock(event)
        } catch (e: Exception) {
            log.error("Failed to process restock notification: adId={}", event.adId, e)
        }
    }

    private fun notifyStockDepleted(event: AdStatusChangedEvent, sellerId: String) {
        notificationService.createNotification(
            userId = sellerId,
            type = NotificationType.AD_STOCK_DEPLETED,
            language = languageProvider.getLanguage(sellerId),
            suggestedAction = SuggestedAction(SuggestedActionType.SELLER_AD_DETAILS, event.adId),
            metadata = mapOf("adId" to event.adId)
        )
    }

    private fun notifyCategoryHidden(event: AdStatusChangedEvent, sellerId: String) {
        notificationService.createNotification(
            userId = sellerId,
            type = NotificationType.AD_CATEGORY_HIDDEN,
            language = languageProvider.getLanguage(sellerId),
            suggestedAction = SuggestedAction(SuggestedActionType.AD_EDIT, event.adId),
            metadata = mapOf("adId" to event.adId)
        )
    }

    private fun notifyWatchlistDeactivated(event: AdStatusChangedEvent, sellerId: String) {
        val watchers = watcherProvider.getWatcherUserIds(event.adId)
        for (watcherId in watchers) {
            if (watcherId == sellerId) continue
            notificationService.createNotification(
                userId = watcherId,
                type = NotificationType.AD_WATCHLIST_DEACTIVATED,
                language = languageProvider.getLanguage(watcherId),
                suggestedAction = SuggestedAction(SuggestedActionType.AD_DETAILS, event.adId),
                metadata = mapOf("adId" to event.adId)
            )
        }
    }

    private fun notifyWatchersOfPriceChange(event: AdPriceChangedEvent) {
        val watchers = watcherProvider.getWatcherUserIds(event.adId)
        for (watcherId in watchers) {
            if (watcherId == event.sellerId) continue
            notificationService.createNotification(
                userId = watcherId,
                type = NotificationType.AD_WATCHLIST_PRICE_CHANGE,
                language = languageProvider.getLanguage(watcherId),
                messageArgs = arrayOf(event.adTitle, event.newPrice),
                suggestedAction = SuggestedAction(SuggestedActionType.AD_DETAILS, event.adId),
                metadata = mapOf("adId" to event.adId)
            )
        }
    }

    private fun notifyWatchersOfRestock(event: AdRestockedEvent) {
        val watchers = watcherProvider.getWatcherUserIds(event.adId)
        for (watcherId in watchers) {
            if (watcherId == event.sellerId) continue
            notificationService.createNotification(
                userId = watcherId,
                type = NotificationType.AD_WATCHLIST_BACK_IN_STOCK,
                language = languageProvider.getLanguage(watcherId),
                messageArgs = arrayOf(event.adTitle),
                suggestedAction = SuggestedAction(SuggestedActionType.AD_DETAILS, event.adId),
                metadata = mapOf("adId" to event.adId)
            )
        }
    }
}

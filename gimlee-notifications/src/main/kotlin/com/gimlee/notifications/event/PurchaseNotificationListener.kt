package com.gimlee.notifications.event

import com.gimlee.events.PurchaseEvent
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PurchaseNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handlePurchaseEvent(event: PurchaseEvent) {
        try {
            when (event.status) {
                STATUS_CREATED -> notifyNewOrder(event)
                STATUS_COMPLETE -> notifyOrderComplete(event)
                STATUS_FAILED_PAYMENT_TIMEOUT -> notifyPaymentTimeout(event)
                STATUS_FAILED_PAYMENT_UNDERPAID -> notifyUnderpaid(event)
                STATUS_CANCELLED -> notifyOrderCancelled(event)
            }
        } catch (e: Exception) {
            log.error("Failed to process purchase notification: purchaseId={}, status={}", event.purchaseId, event.status, e)
        }
    }

    private fun notifyNewOrder(event: PurchaseEvent) {
        val sellerId = event.sellerId.toHexString()
        val totalQuantity = event.items.sumOf { it.quantity }
        notificationService.createNotification(
            userId = sellerId,
            type = NotificationType.ORDER_NEW,
            language = languageProvider.getLanguage(sellerId),
            messageArgs = arrayOf("$totalQuantity item(s)"),
            suggestedAction = SuggestedAction(SuggestedActionType.ORDER_DETAILS, event.purchaseId.toHexString()),
            metadata = mapOf("purchaseId" to event.purchaseId.toHexString())
        )
    }

    private fun notifyOrderComplete(event: PurchaseEvent) {
        val purchaseId = event.purchaseId.toHexString()
        val buyerId = event.buyerId.toHexString()
        val sellerId = event.sellerId.toHexString()

        notificationService.createNotification(
            userId = buyerId,
            type = NotificationType.ORDER_COMPLETE,
            language = languageProvider.getLanguage(buyerId),
            suggestedAction = SuggestedAction(SuggestedActionType.PURCHASE_LIST),
            metadata = mapOf("purchaseId" to purchaseId)
        )
        notificationService.createNotification(
            userId = sellerId,
            type = NotificationType.ORDER_COMPLETE,
            language = languageProvider.getLanguage(sellerId),
            suggestedAction = SuggestedAction(SuggestedActionType.ORDER_DETAILS, purchaseId),
            metadata = mapOf("purchaseId" to purchaseId)
        )
    }

    private fun notifyPaymentTimeout(event: PurchaseEvent) {
        val buyerId = event.buyerId.toHexString()
        notificationService.createNotification(
            userId = buyerId,
            type = NotificationType.ORDER_PAYMENT_TIMEOUT,
            language = languageProvider.getLanguage(buyerId),
            suggestedAction = SuggestedAction(SuggestedActionType.PURCHASE_LIST),
            metadata = mapOf("purchaseId" to event.purchaseId.toHexString())
        )
    }

    private fun notifyUnderpaid(event: PurchaseEvent) {
        val buyerId = event.buyerId.toHexString()
        notificationService.createNotification(
            userId = buyerId,
            type = NotificationType.ORDER_UNDERPAID,
            language = languageProvider.getLanguage(buyerId),
            messageArgs = arrayOf(event.totalAmount.toPlainString(), "—"),
            suggestedAction = SuggestedAction(SuggestedActionType.PURCHASE_LIST),
            metadata = mapOf("purchaseId" to event.purchaseId.toHexString())
        )
    }

    private fun notifyOrderCancelled(event: PurchaseEvent) {
        val purchaseId = event.purchaseId.toHexString()
        val buyerId = event.buyerId.toHexString()
        val sellerId = event.sellerId.toHexString()

        notificationService.createNotification(
            userId = buyerId,
            type = NotificationType.ORDER_CANCELLED,
            language = languageProvider.getLanguage(buyerId),
            suggestedAction = SuggestedAction(SuggestedActionType.PURCHASE_LIST),
            metadata = mapOf("purchaseId" to purchaseId)
        )
        notificationService.createNotification(
            userId = sellerId,
            type = NotificationType.ORDER_CANCELLED,
            language = languageProvider.getLanguage(sellerId),
            suggestedAction = SuggestedAction(SuggestedActionType.ORDER_DETAILS, purchaseId),
            metadata = mapOf("purchaseId" to purchaseId)
        )
    }

    companion object {
        private const val STATUS_CREATED = 0
        private const val STATUS_COMPLETE = 2
        private const val STATUS_FAILED_PAYMENT_TIMEOUT = 3
        private const val STATUS_FAILED_PAYMENT_UNDERPAID = 4
        private const val STATUS_CANCELLED = 5
    }
}

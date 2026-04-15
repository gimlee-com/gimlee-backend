package com.gimlee.notifications.event

import com.gimlee.events.PaymentDeadlineApproachingEvent
import com.gimlee.events.PaymentEvent
import com.gimlee.notifications.domain.NotificationService
import com.gimlee.notifications.domain.UserLanguageProvider
import com.gimlee.notifications.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class PaymentNotificationListener(
    private val notificationService: NotificationService,
    private val languageProvider: UserLanguageProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handlePaymentEvent(event: PaymentEvent) {
        try {
            when (event.status) {
                STATUS_AWAITING_CONFIRMATION -> notifyAwaitingPayment(event)
                STATUS_COMPLETE_OVERPAID -> notifyOverpaid(event)
                STATUS_FAILED_SOFT_TIMEOUT, STATUS_FAILED_HARD_TIMEOUT -> notifyPaymentTimeout(event)
            }
        } catch (e: Exception) {
            log.error("Failed to process payment notification: purchaseId={}, status={}", event.purchaseId, event.status, e)
        }
    }

    private fun notifyAwaitingPayment(event: PaymentEvent) {
        val buyerId = event.buyerId.toHexString()
        notificationService.createNotification(
            userId = buyerId,
            type = NotificationType.ORDER_AWAITING_PAYMENT,
            language = languageProvider.getLanguage(buyerId),
            messageArgs = arrayOf(event.amount.toPlainString()),
            suggestedAction = SuggestedAction(SuggestedActionType.PURCHASE_LIST),
            metadata = mapOf("purchaseId" to event.purchaseId.toHexString())
        )
    }

    private fun notifyOverpaid(event: PaymentEvent) {
        val purchaseId = event.purchaseId.toHexString()
        val buyerId = event.buyerId.toHexString()
        val sellerId = event.sellerId.toHexString()

        notificationService.createNotification(
            userId = buyerId,
            type = NotificationType.ORDER_OVERPAID,
            language = languageProvider.getLanguage(buyerId),
            messageArgs = arrayOf(event.amount.toPlainString()),
            suggestedAction = SuggestedAction(SuggestedActionType.PURCHASE_LIST),
            metadata = mapOf("purchaseId" to purchaseId)
        )
        notificationService.createNotification(
            userId = sellerId,
            type = NotificationType.ORDER_OVERPAID,
            language = languageProvider.getLanguage(sellerId),
            messageArgs = arrayOf(event.amount.toPlainString()),
            suggestedAction = SuggestedAction(SuggestedActionType.ORDER_DETAILS, purchaseId),
            metadata = mapOf("purchaseId" to purchaseId)
        )
    }

    private fun notifyPaymentTimeout(event: PaymentEvent) {
        val buyerId = event.buyerId.toHexString()
        notificationService.createNotification(
            userId = buyerId,
            type = NotificationType.ORDER_PAYMENT_TIMEOUT,
            language = languageProvider.getLanguage(buyerId),
            suggestedAction = SuggestedAction(SuggestedActionType.PURCHASE_LIST),
            metadata = mapOf("purchaseId" to event.purchaseId.toHexString())
        )
    }

    companion object {
        private const val STATUS_AWAITING_CONFIRMATION = 1
        private const val STATUS_COMPLETE_OVERPAID = 3
        private const val STATUS_FAILED_SOFT_TIMEOUT = 5
        private const val STATUS_FAILED_HARD_TIMEOUT = 6
    }

    @Async
    @EventListener
    fun handleDeadlineApproaching(event: PaymentDeadlineApproachingEvent) {
        try {
            val buyerId = event.buyerId.toHexString()
            notificationService.createNotification(
                userId = buyerId,
                type = NotificationType.ORDER_PAYMENT_DEADLINE,
                language = languageProvider.getLanguage(buyerId),
                messageArgs = arrayOf(event.amount.toPlainString()),
                suggestedAction = SuggestedAction(SuggestedActionType.PURCHASE_LIST),
                metadata = mapOf("purchaseId" to event.purchaseId.toHexString())
            )
        } catch (e: Exception) {
            log.error("Failed to process deadline notification: purchaseId={}", event.purchaseId, e)
        }
    }
}

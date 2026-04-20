package com.gimlee.api.chat

import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.web.ChatEventBroadcaster
import com.gimlee.events.PurchaseEvent
import com.gimlee.purchases.domain.model.PurchaseStatus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Locks order conversations when the purchase reaches a terminal state.
 */
@Component
class PurchaseConversationLockListener(
    private val conversationService: ConversationService,
    private val chatEventBroadcaster: ChatEventBroadcaster,
    private val properties: OrderConversationProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val terminalStatuses = setOf(
        PurchaseStatus.COMPLETE.id,
        PurchaseStatus.FAILED_PAYMENT_TIMEOUT.id,
        PurchaseStatus.FAILED_PAYMENT_UNDERPAID.id,
        PurchaseStatus.CANCELLED.id
    )

    private val failedStatuses = setOf(
        PurchaseStatus.FAILED_PAYMENT_TIMEOUT.id,
        PurchaseStatus.FAILED_PAYMENT_UNDERPAID.id,
        PurchaseStatus.CANCELLED.id
    )

    @EventListener
    fun onPurchaseEvent(event: PurchaseEvent) {
        if (event.status !in terminalStatuses) return

        val shouldLock = when {
            event.status == PurchaseStatus.COMPLETE.id -> properties.lockOnComplete
            event.status in failedStatuses -> properties.lockOnFailed
            else -> false
        }
        if (!shouldLock) return

        val purchaseId = event.purchaseId.toHexString()
        val conversation = conversationService.findByLink(ConversationLinkTypes.PURCHASE, purchaseId)
        if (conversation == null) {
            log.warn("No conversation found for purchase {} to lock", purchaseId)
            return
        }

        val locked = conversationService.lockConversation(conversation.id)
        if (locked) {
            log.info("Locked conversation {} for completed purchase {}", conversation.id, purchaseId)
            chatEventBroadcaster.closeEmittersForConversation(conversation.id)
        }
    }
}

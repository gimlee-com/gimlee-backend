package com.gimlee.api.chat

import com.gimlee.chat.domain.ChatService
import com.gimlee.chat.domain.ConversationService
import com.gimlee.events.PurchaseEvent
import com.gimlee.purchases.domain.model.PurchaseStatus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Posts system messages to order conversations on purchase status changes.
 */
@Component
class PurchaseStatusMessageListener(
    private val conversationService: ConversationService,
    private val chatService: ChatService,
    private val properties: OrderConversationProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val SYSTEM_CODE_STATUS_CHANGED = "PURCHASE_STATUS_CHANGED"
    }

    @EventListener
    fun onPurchaseEvent(event: PurchaseEvent) {
        if (!properties.systemMessagesEnabled) return
        if (event.status == PurchaseStatus.CREATED.id) return

        val purchaseId = event.purchaseId.toHexString()
        val statusName = PurchaseStatus.entries.find { it.id == event.status }?.name ?: return

        val conversation = conversationService.findByLink(ConversationLinkTypes.PURCHASE, purchaseId)
        if (conversation == null) {
            log.warn("No conversation found for purchase {} to post status message", purchaseId)
            return
        }

        chatService.sendSystemMessage(
            chatId = conversation.id,
            systemCode = SYSTEM_CODE_STATUS_CHANGED,
            systemArgs = mapOf("status" to statusName, "purchaseId" to purchaseId)
        )
    }
}

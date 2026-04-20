package com.gimlee.api.chat

import com.gimlee.chat.domain.ConversationService
import com.gimlee.chat.domain.model.conversation.ParticipantRole
import com.gimlee.events.PurchaseEvent
import com.gimlee.purchases.domain.model.PurchaseStatus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Creates a conversation between buyer and seller when a purchase is created.
 * This is synchronous (@EventListener) so failure propagates to the purchase transaction.
 */
@Component
class PurchaseConversationListener(
    private val conversationService: ConversationService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onPurchaseEvent(event: PurchaseEvent) {
        if (event.status != PurchaseStatus.CREATED.id) return

        val buyerId = event.buyerId.toHexString()
        val sellerId = event.sellerId.toHexString()
        val purchaseId = event.purchaseId.toHexString()

        val (conversation, error) = conversationService.createConversation(
            type = ConversationTypes.ORDER,
            participantUserIds = listOf(buyerId, sellerId),
            participantRole = ParticipantRole.MEMBER,
            linkType = ConversationLinkTypes.PURCHASE,
            linkId = purchaseId
        )

        if (error != null) {
            log.warn("Failed to create order conversation for purchase {}: {}", purchaseId, error)
        } else {
            log.info("Created order conversation {} for purchase {}", conversation.id, purchaseId)
        }
    }
}

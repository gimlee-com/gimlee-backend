package com.gimlee.api.chat

import com.gimlee.auth.service.UserService
import com.gimlee.chat.domain.ConversationTitleProvider
import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.purchases.domain.PurchaseService
import org.bson.types.ObjectId
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
class OrderConversationTitleProvider(
    private val purchaseService: PurchaseService,
    private val userService: UserService,
    private val messageSource: MessageSource
) : ConversationTitleProvider {

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("UTC"))
        private const val TRUNCATED_ID_LENGTH = 8
    }

    override val priority: Int = 10

    override fun canHandle(conversation: Conversation): Boolean {
        return conversation.type == ConversationTypes.ORDER && conversation.linkType == ConversationLinkTypes.PURCHASE
    }

    override fun getTitles(
        conversations: List<Conversation>,
        currentUserId: String,
        locale: Locale
    ): Map<String, String> {
        val purchaseIds = conversations.mapNotNull { it.linkId }.distinct()
        val oids = purchaseIds.mapNotNull {
            try { ObjectId(it) } catch (e: Exception) { null }
        }
        
        // Batch fetch purchases
        val purchases = purchaseService.getPurchases(oids).associateBy { it.id.toHexString() }

        // Batch fetch all participant usernames (both buyers and sellers)
        val allParticipantIds = purchases.values.flatMap { listOf(it.sellerId.toHexString(), it.buyerId.toHexString()) }.distinct()
        val usernames = userService.findUsernamesByIds(allParticipantIds)

        return conversations.associate { conv ->
            val purchase = purchases[conv.linkId]
            val title = if (purchase != null) {
                val otherParticipantId = if (purchase.buyerId.toHexString() == currentUserId) {
                    purchase.sellerId.toHexString()
                } else {
                    purchase.buyerId.toHexString()
                }
                
                val otherParticipantName = usernames[otherParticipantId] ?: "Unknown"
                val date = DATE_FORMATTER.format(purchase.createdAt)
                val truncatedId = purchase.id.toHexString().take(TRUNCATED_ID_LENGTH)
                
                messageSource.getMessage(
                    "gimlee.chat.conversation.order.title",
                    arrayOf(truncatedId, otherParticipantName, date),
                    locale
                )
            } else {
                // Fallback if purchase not found
                messageSource.getMessage(
                    "gimlee.chat.conversation.default.with-names",
                    arrayOf("Order ${conv.linkId}"),
                    locale
                )
            }
            conv.id to title
        }
    }
}

package com.gimlee.api.chat

import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationStatus
import com.gimlee.chat.domain.policy.ConversationPolicy
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class GimleeConversationPolicy : ConversationPolicy {

    override fun maxParticipants(type: String): Int = when (type) {
        ConversationTypes.ORDER -> 2
        else -> 50
    }

    override fun allowMessageSend(conversation: Conversation, userId: String): Boolean =
        conversation.status == ConversationStatus.ACTIVE && conversation.isParticipant(userId)

    override fun allowMessageRead(conversation: Conversation, userId: String): Boolean =
        conversation.status != ConversationStatus.ARCHIVED && conversation.isParticipant(userId)
}

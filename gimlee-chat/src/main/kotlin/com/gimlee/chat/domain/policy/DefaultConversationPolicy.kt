package com.gimlee.chat.domain.policy

import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationStatus
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Int.MAX_VALUE)
class DefaultConversationPolicy : ConversationPolicy {

    companion object {
        private const val DEFAULT_MAX_PARTICIPANTS = 50
    }

    override fun maxParticipants(type: String): Int = DEFAULT_MAX_PARTICIPANTS

    override fun allowMessageSend(conversation: Conversation, userId: String): Boolean =
        conversation.status == ConversationStatus.ACTIVE && conversation.isParticipant(userId)

    override fun allowMessageRead(conversation: Conversation, userId: String): Boolean =
        conversation.status != ConversationStatus.ARCHIVED && conversation.isParticipant(userId)
}

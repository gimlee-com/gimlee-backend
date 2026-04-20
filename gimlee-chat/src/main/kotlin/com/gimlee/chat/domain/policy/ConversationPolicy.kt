package com.gimlee.chat.domain.policy

import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationStatus

/**
 * Policy interface for controlling conversation behavior.
 * Consumers can provide their own implementation via Spring @Order to override defaults.
 */
interface ConversationPolicy {
    fun maxParticipants(type: String): Int
    fun allowMessageSend(conversation: Conversation, userId: String): Boolean
    fun allowMessageRead(conversation: Conversation, userId: String): Boolean
}

package com.gimlee.chat.domain.event

import com.gimlee.chat.domain.model.MessageType
import java.time.Instant

/**
 * Internal event to broadcast chat events (messages, typing indicators) to real-time SSE subscribers.
 * This is JVM-local — requires sticky routing or distributed bus for horizontal scaling.
 */
data class InternalChatEvent(
    val chatId: String,
    val type: String,
    val data: String? = null,
    val authorId: String? = null,
    val author: String,
    val messageType: MessageType = MessageType.REGULAR,
    val systemCode: String? = null,
    val systemArgs: Map<String, String>? = null,
    val timestamp: Instant = Instant.now()
)

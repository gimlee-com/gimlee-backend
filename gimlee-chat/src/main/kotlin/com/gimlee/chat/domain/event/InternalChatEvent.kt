package com.gimlee.chat.domain.event

import com.gimlee.chat.domain.model.MessageType
import java.time.Instant

/**
 * Internal event to broadcast chat events (messages, typing indicators) to real-time SSE subscribers.
 *
 * Delivered via [com.gimlee.chat.web.transport.ChatEventTransport]: in-process for single-node
 * deployments, or Redis Pub/Sub for horizontal scaling.
 * See docs/adr/0005-real-time-chat-horizontal-scaling.md.
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

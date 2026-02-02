package com.gimlee.events

import java.time.Instant

/**
 * Event published when a new message is sent in a chat.
 */
data class ChatMessageSentEvent(
    val chatId: String,
    val author: String,
    val text: String,
    val timestamp: Instant = Instant.now()
)

/**
 * Internal event to broadcast chat events (messages, typing indicators) to real-time subscribers.
 */
data class InternalChatEvent(
    val chatId: String,
    val type: String, // "MESSAGE", "TYPING_INDICATOR"
    val data: String? = null,
    val author: String,
    val timestamp: Instant = Instant.now()
)

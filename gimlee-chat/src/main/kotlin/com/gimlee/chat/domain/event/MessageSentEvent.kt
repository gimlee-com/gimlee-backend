package com.gimlee.chat.domain.event

import java.time.Instant

/**
 * Published when a new message is sent in a conversation.
 */
data class MessageSentEvent(
    val conversationId: String,
    val messageId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val timestamp: Instant = Instant.now()
)

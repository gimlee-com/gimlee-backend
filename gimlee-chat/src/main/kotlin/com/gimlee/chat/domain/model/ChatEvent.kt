package com.gimlee.chat.domain.model

import java.time.Instant

data class ChatEvent(
    val id: String,
    val chatId: String,
    val type: ChatEventType,
    val data: String? = null,
    val author: String,
    val timestamp: Instant,
    val own: Boolean = false
)

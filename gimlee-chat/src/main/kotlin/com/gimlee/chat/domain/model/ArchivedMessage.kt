package com.gimlee.chat.domain.model

import java.time.Instant

data class ArchivedMessage(
    val id: String,
    val chatId: String,
    val text: String?,
    val authorId: String,
    val author: String,
    val messageType: MessageType = MessageType.REGULAR,
    val systemCode: String? = null,
    val systemArgs: Map<String, String>? = null,
    val timestamp: Instant
)

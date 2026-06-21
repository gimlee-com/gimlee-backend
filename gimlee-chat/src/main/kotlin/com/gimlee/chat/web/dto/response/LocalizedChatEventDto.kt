package com.gimlee.chat.web.dto.response

import com.gimlee.chat.domain.model.MessageType
import java.time.Instant

data class LocalizedChatEventDto(
    val chatId: String,
    val type: String,
    val data: String? = null,
    val authorId: String? = null,
    val author: String,
    val messageType: MessageType,
    val timestamp: Instant
)

package com.gimlee.chat.domain.model

import java.time.Instant

data class ArchivedMessage(
    val id: String,
    val chatId: String,
    val text: String,
    val author: String,
    val timestamp: Instant
)

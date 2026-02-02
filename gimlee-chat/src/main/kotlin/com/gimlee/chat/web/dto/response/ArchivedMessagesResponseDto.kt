package com.gimlee.chat.web.dto.response

import com.gimlee.chat.domain.model.ArchivedMessage

data class ArchivedMessagesResponseDto(
    val hasMore: Boolean,
    val messages: List<ArchivedMessage>
)

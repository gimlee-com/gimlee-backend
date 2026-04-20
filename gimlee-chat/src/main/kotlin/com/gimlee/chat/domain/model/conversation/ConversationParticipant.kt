package com.gimlee.chat.domain.model.conversation

import java.time.Instant

data class ConversationParticipant(
    val userId: String,
    val role: ParticipantRole,
    val joinedAt: Instant
)

package com.gimlee.chat.domain.model.conversation

import java.time.Instant

data class Conversation(
    val id: String,
    val type: String,
    val participants: List<ConversationParticipant>,
    val linkType: String? = null,
    val linkId: String? = null,
    val status: ConversationStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastActivityAt: Instant
) {
    companion object {
        val EMPTY = Conversation(
            id = "",
            type = "",
            participants = emptyList(),
            status = ConversationStatus.ARCHIVED,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            lastActivityAt = Instant.EPOCH
        )
    }

    fun isParticipant(userId: String): Boolean =
        participants.any { it.userId == userId }

    fun getParticipant(userId: String): ConversationParticipant? =
        participants.find { it.userId == userId }
}

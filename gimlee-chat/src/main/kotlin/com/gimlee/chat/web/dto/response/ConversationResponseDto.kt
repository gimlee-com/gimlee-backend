package com.gimlee.chat.web.dto.response

import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationParticipant
import java.time.Instant

data class ConversationResponseDto(
    val id: String,
    val type: String,
    val participants: List<ConversationParticipantDto>,
    val linkType: String?,
    val linkId: String?,
    val status: String,
    val createdAt: Instant,
    val lastActivityAt: Instant
) {
    companion object {
        fun from(conversation: Conversation): ConversationResponseDto = ConversationResponseDto(
            id = conversation.id,
            type = conversation.type,
            participants = conversation.participants.map { ConversationParticipantDto.from(it) },
            linkType = conversation.linkType,
            linkId = conversation.linkId,
            status = conversation.status.name,
            createdAt = conversation.createdAt,
            lastActivityAt = conversation.lastActivityAt
        )
    }
}

data class ConversationParticipantDto(
    val userId: String,
    val role: String,
    val joinedAt: Instant
) {
    companion object {
        fun from(participant: ConversationParticipant): ConversationParticipantDto = ConversationParticipantDto(
            userId = participant.userId,
            role = participant.role.name,
            joinedAt = participant.joinedAt
        )
    }
}

data class ConversationListResponseDto(
    val hasMore: Boolean,
    val conversations: List<ConversationResponseDto>
)

package com.gimlee.chat.web.dto.response

import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationParticipant
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Detailed information about a conversation")
data class ConversationResponseDto(
    @field:Schema(description = "Conversation ID")
    val id: String,
    @field:Schema(description = "Conversation type (e.g. ORDER, PRIVATE)")
    val type: String,
    @field:Schema(description = "List of conversation participants")
    val participants: List<ConversationParticipantDto>,
    @field:Schema(description = "Type of the entity this conversation is linked to")
    val linkType: String?,
    @field:Schema(description = "ID of the entity this conversation is linked to")
    val linkId: String?,
    @field:Schema(description = "Display name of the conversation")
    val title: String?,
    @field:Schema(description = "Conversation status (ACTIVE, LOCKED, ARCHIVED)")
    val status: String,
    @field:Schema(description = "When the conversation was created")
    val createdAt: Instant,
    @field:Schema(description = "When the last activity occurred")
    val lastActivityAt: Instant
) {
    companion object {
        fun from(conversation: Conversation, title: String? = null): ConversationResponseDto = ConversationResponseDto(
            id = conversation.id,
            type = conversation.type,
            participants = conversation.participants.map { ConversationParticipantDto.from(it) },
            linkType = conversation.linkType,
            linkId = conversation.linkId,
            title = title,
            status = conversation.status.name,
            createdAt = conversation.createdAt,
            lastActivityAt = conversation.lastActivityAt
        )
    }
}

@Schema(description = "Information about a conversation participant")
data class ConversationParticipantDto(
    @field:Schema(description = "User ID")
    val userId: String,
    @field:Schema(description = "Participant role (e.g. OWNER, MEMBER)")
    val role: String,
    @field:Schema(description = "When the participant joined")
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

@Schema(description = "Paginated list of conversations")
data class ConversationListResponseDto(
    @field:Schema(description = "Whether there are more conversations to fetch")
    val hasMore: Boolean,
    @field:Schema(description = "List of conversations")
    val conversations: List<ConversationResponseDto>
)

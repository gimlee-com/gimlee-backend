package com.gimlee.chat.persistence.model

import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationParticipant
import com.gimlee.chat.domain.model.conversation.ConversationStatus
import com.gimlee.chat.domain.model.conversation.ParticipantRole
import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import org.bson.types.ObjectId

data class ConversationDocument(
    val id: ObjectId,
    val type: String,
    val participants: List<ConversationParticipantDocument>,
    val linkType: String?,
    val linkId: String?,
    val status: String,
    val createdAtMicros: Long,
    val updatedAtMicros: Long,
    val lastActivityAtMicros: Long
) {
    companion object {
        const val COLLECTION_NAME = "gimlee-chat-conversations"

        const val FIELD_ID = "_id"
        const val FIELD_TYPE = "tp"
        const val FIELD_PARTICIPANTS = "pts"
        const val FIELD_PARTICIPANT_USER_ID = "uid"
        const val FIELD_PARTICIPANT_ROLE = "rl"
        const val FIELD_PARTICIPANT_JOINED_AT = "jat"
        const val FIELD_LINK_TYPE = "lti"
        const val FIELD_LINK_ID = "lid"
        const val FIELD_STATUS = "st"
        const val FIELD_CREATED_AT = "cat"
        const val FIELD_UPDATED_AT = "uat"
        const val FIELD_LAST_ACTIVITY_AT = "lat"

        fun fromDomain(domain: Conversation): ConversationDocument = ConversationDocument(
            id = ObjectId(domain.id),
            type = domain.type,
            participants = domain.participants.map { ConversationParticipantDocument.fromDomain(it) },
            linkType = domain.linkType,
            linkId = domain.linkId,
            status = ConversationStatus.entries.first { it == domain.status }.shortName,
            createdAtMicros = domain.createdAt.toMicros(),
            updatedAtMicros = domain.updatedAt.toMicros(),
            lastActivityAtMicros = domain.lastActivityAt.toMicros()
        )
    }

    fun toDomain(): Conversation = Conversation(
        id = id.toHexString(),
        type = type,
        participants = participants.map { it.toDomain() },
        linkType = linkType,
        linkId = linkId,
        status = ConversationStatus.fromShortName(status),
        createdAt = fromMicros(createdAtMicros),
        updatedAt = fromMicros(updatedAtMicros),
        lastActivityAt = fromMicros(lastActivityAtMicros)
    )
}

data class ConversationParticipantDocument(
    val userId: String,
    val role: String,
    val joinedAtMicros: Long
) {
    companion object {
        fun fromDomain(domain: ConversationParticipant): ConversationParticipantDocument =
            ConversationParticipantDocument(
                userId = domain.userId,
                role = domain.role.shortName,
                joinedAtMicros = domain.joinedAt.toMicros()
            )
    }

    fun toDomain(): ConversationParticipant = ConversationParticipant(
        userId = userId,
        role = ParticipantRole.fromShortName(role),
        joinedAt = fromMicros(joinedAtMicros)
    )
}

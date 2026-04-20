package com.gimlee.chat.domain

import com.gimlee.chat.domain.event.ConversationEvent
import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationParticipant
import com.gimlee.chat.domain.model.conversation.ConversationStatus
import com.gimlee.chat.domain.model.conversation.ParticipantRole
import com.gimlee.chat.domain.policy.ConversationPolicy
import com.gimlee.common.UUIDv7
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.toMicros
import com.gimlee.chat.persistence.ConversationRepository
import org.bson.types.ObjectId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ConversationService(
    private val conversationRepository: ConversationRepository,
    private val conversationPolicies: List<ConversationPolicy>,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val conversationPolicy: ConversationPolicy = conversationPolicies.first()

    /**
     * Creates a new conversation. If linkType/linkId are provided and a conversation
     * with that link already exists, returns the existing one (idempotent).
     */
    fun createConversation(
        type: String,
        participantUserIds: List<String>,
        participantRole: ParticipantRole = ParticipantRole.MEMBER,
        linkType: String? = null,
        linkId: String? = null
    ): Pair<Conversation, ChatOutcome?> {
        val maxParticipants = conversationPolicy.maxParticipants(type)
        if (participantUserIds.size > maxParticipants) {
            return Pair(Conversation.EMPTY, ChatOutcome.MAX_PARTICIPANTS_EXCEEDED)
        }

        // Check if a linked conversation already exists
        if (linkType != null && linkId != null) {
            val existing = conversationRepository.findByLink(linkType, linkId)
            if (existing != null) return Pair(existing, null)
        }

        val now = Instant.now()
        val participants = participantUserIds.map { userId ->
            ConversationParticipant(
                userId = userId,
                role = participantRole,
                joinedAt = now
            )
        }

        val conversation = Conversation(
            id = ObjectId.get().toHexString(),
            type = type,
            participants = participants,
            linkType = linkType,
            linkId = linkId,
            status = ConversationStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
            lastActivityAt = now
        )

        val created = conversationRepository.save(conversation)

        if (!created) {
            // Unique index prevented insert — fetch the existing one
            if (linkType != null && linkId != null) {
                val existing = conversationRepository.findByLink(linkType, linkId)
                if (existing != null) return Pair(existing, null)
            }
            return Pair(Conversation.EMPTY, ChatOutcome.CONVERSATION_ALREADY_EXISTS)
        }

        eventPublisher.publishEvent(ConversationEvent(
            conversationId = conversation.id,
            type = type,
            cause = "CREATED",
            details = mapOf("participantUserIds" to participantUserIds),
            linkType = linkType,
            linkId = linkId
        ))

        return Pair(conversation, null)
    }

    fun findById(conversationId: String): Conversation? =
        conversationRepository.findById(conversationId)

    fun findByLink(linkType: String, linkId: String): Conversation? =
        conversationRepository.findByLink(linkType, linkId)

    fun findByParticipant(userId: String, limit: Int, beforeActivityAtMicros: Long? = null): List<Conversation> =
        conversationRepository.findByParticipant(userId, limit, beforeActivityAtMicros)

    /**
     * Verifies the user has write access. Returns null if access is granted,
     * or the relevant ChatOutcome if denied.
     */
    fun verifyWriteAccess(conversationId: String, userId: String): ChatOutcome? {
        val conversation = conversationRepository.findById(conversationId)
            ?: return ChatOutcome.CONVERSATION_NOT_FOUND

        if (!conversationPolicy.allowMessageSend(conversation, userId)) {
            return when {
                !conversation.isParticipant(userId) -> ChatOutcome.NOT_A_PARTICIPANT
                conversation.status == ConversationStatus.LOCKED -> ChatOutcome.CONVERSATION_LOCKED
                conversation.status == ConversationStatus.ARCHIVED -> ChatOutcome.CONVERSATION_ARCHIVED
                else -> ChatOutcome.UNAUTHORIZED_ACCESS
            }
        }

        return null
    }

    /**
     * Verifies the user has read access. Returns null if access is granted,
     * or the relevant ChatOutcome if denied.
     */
    fun verifyReadAccess(conversationId: String, userId: String): ChatOutcome? {
        val conversation = conversationRepository.findById(conversationId)
            ?: return ChatOutcome.CONVERSATION_NOT_FOUND

        if (!conversationPolicy.allowMessageRead(conversation, userId)) {
            return when {
                !conversation.isParticipant(userId) -> ChatOutcome.NOT_A_PARTICIPANT
                conversation.status == ConversationStatus.ARCHIVED -> ChatOutcome.CONVERSATION_ARCHIVED
                else -> ChatOutcome.UNAUTHORIZED_ACCESS
            }
        }

        return null
    }

    fun updateLastActivity(conversationId: String) {
        conversationRepository.updateLastActivity(conversationId, Instant.now().toMicros())
    }

    fun lockConversation(conversationId: String): Boolean =
        conversationRepository.updateStatus(conversationId, ConversationStatus.LOCKED)

    fun archiveConversation(conversationId: String): Boolean =
        conversationRepository.updateStatus(conversationId, ConversationStatus.ARCHIVED)
}

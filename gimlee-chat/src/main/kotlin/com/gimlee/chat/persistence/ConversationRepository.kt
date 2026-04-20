package com.gimlee.chat.persistence

import com.gimlee.chat.domain.model.conversation.Conversation
import com.gimlee.chat.domain.model.conversation.ConversationStatus
import com.gimlee.chat.persistence.model.ConversationDocument
import com.gimlee.chat.persistence.model.ConversationParticipantDocument
import com.gimlee.common.persistence.mongo.MongoExceptionUtils
import com.gimlee.common.toMicros
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ConversationRepository(mongoDatabase: MongoDatabase) {

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(ConversationDocument.COLLECTION_NAME)
    }

    /**
     * Saves a new conversation. Returns true if created, false if a duplicate link already exists.
     */
    fun save(conversation: Conversation): Boolean {
        val document = mapToDocument(ConversationDocument.fromDomain(conversation))
        return try {
            collection.insertOne(document)
            true
        } catch (e: Exception) {
            if (MongoExceptionUtils.isDuplicateKeyException(e)) false
            else throw e
        }
    }

    fun findById(id: String): Conversation? {
        return try {
            collection.find(Filters.eq(ConversationDocument.FIELD_ID, ObjectId(id)))
                .first()
                ?.let { mapToConversationDocument(it).toDomain() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun findByLink(linkType: String, linkId: String): Conversation? {
        val filter = Filters.and(
            Filters.eq(ConversationDocument.FIELD_LINK_TYPE, linkType),
            Filters.eq(ConversationDocument.FIELD_LINK_ID, linkId)
        )
        return collection.find(filter)
            .first()
            ?.let { mapToConversationDocument(it).toDomain() }
    }

    fun findByParticipant(userId: String, limit: Int, beforeActivityAtMicros: Long? = null): List<Conversation> {
        val participantPath = "${ConversationDocument.FIELD_PARTICIPANTS}.${ConversationDocument.FIELD_PARTICIPANT_USER_ID}"
        val filters = mutableListOf(
            Filters.eq(participantPath, userId),
            Filters.ne(ConversationDocument.FIELD_STATUS, ConversationStatus.ARCHIVED.shortName)
        )

        beforeActivityAtMicros?.let {
            filters.add(Filters.lt(ConversationDocument.FIELD_LAST_ACTIVITY_AT, it))
        }

        return collection.find(Filters.and(filters))
            .sort(Sorts.descending(ConversationDocument.FIELD_LAST_ACTIVITY_AT))
            .limit(limit)
            .map { mapToConversationDocument(it).toDomain() }
            .toList()
    }

    fun updateStatus(conversationId: String, status: ConversationStatus): Boolean {
        val now = Instant.now().toMicros()
        val result = collection.updateOne(
            Filters.eq(ConversationDocument.FIELD_ID, ObjectId(conversationId)),
            Updates.combine(
                Updates.set(ConversationDocument.FIELD_STATUS, status.shortName),
                Updates.set(ConversationDocument.FIELD_UPDATED_AT, now)
            )
        )
        return result.modifiedCount > 0
    }

    fun updateLastActivity(conversationId: String, timestampMicros: Long): Boolean {
        val result = collection.updateOne(
            Filters.eq(ConversationDocument.FIELD_ID, ObjectId(conversationId)),
            Updates.combine(
                Updates.set(ConversationDocument.FIELD_LAST_ACTIVITY_AT, timestampMicros),
                Updates.set(ConversationDocument.FIELD_UPDATED_AT, timestampMicros)
            )
        )
        return result.modifiedCount > 0
    }

    fun clear() {
        collection.deleteMany(Document())
    }

    private fun mapToDocument(doc: ConversationDocument): Document {
        val participantDocs = doc.participants.map { p ->
            Document()
                .append(ConversationDocument.FIELD_PARTICIPANT_USER_ID, p.userId)
                .append(ConversationDocument.FIELD_PARTICIPANT_ROLE, p.role)
                .append(ConversationDocument.FIELD_PARTICIPANT_JOINED_AT, p.joinedAtMicros)
        }

        return Document()
            .append(ConversationDocument.FIELD_ID, doc.id)
            .append(ConversationDocument.FIELD_TYPE, doc.type)
            .append(ConversationDocument.FIELD_PARTICIPANTS, participantDocs)
            .apply {
                if (doc.linkType != null) append(ConversationDocument.FIELD_LINK_TYPE, doc.linkType)
                if (doc.linkId != null) append(ConversationDocument.FIELD_LINK_ID, doc.linkId)
            }
            .append(ConversationDocument.FIELD_STATUS, doc.status)
            .append(ConversationDocument.FIELD_CREATED_AT, doc.createdAtMicros)
            .append(ConversationDocument.FIELD_UPDATED_AT, doc.updatedAtMicros)
            .append(ConversationDocument.FIELD_LAST_ACTIVITY_AT, doc.lastActivityAtMicros)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToConversationDocument(doc: Document): ConversationDocument {
        val participantDocs = (doc.get(ConversationDocument.FIELD_PARTICIPANTS) as List<Document>).map { p ->
            ConversationParticipantDocument(
                userId = p.getString(ConversationDocument.FIELD_PARTICIPANT_USER_ID),
                role = p.getString(ConversationDocument.FIELD_PARTICIPANT_ROLE),
                joinedAtMicros = p.getLong(ConversationDocument.FIELD_PARTICIPANT_JOINED_AT)
            )
        }

        return ConversationDocument(
            id = doc.getObjectId(ConversationDocument.FIELD_ID),
            type = doc.getString(ConversationDocument.FIELD_TYPE),
            participants = participantDocs,
            linkType = doc.getString(ConversationDocument.FIELD_LINK_TYPE),
            linkId = doc.getString(ConversationDocument.FIELD_LINK_ID),
            status = doc.getString(ConversationDocument.FIELD_STATUS),
            createdAtMicros = doc.getLong(ConversationDocument.FIELD_CREATED_AT),
            updatedAtMicros = doc.getLong(ConversationDocument.FIELD_UPDATED_AT),
            lastActivityAtMicros = doc.getLong(ConversationDocument.FIELD_LAST_ACTIVITY_AT)
        )
    }
}

package com.gimlee.user.persistence

import com.gimlee.user.domain.model.UserPresenceStatus
import com.gimlee.user.persistence.model.UserPresenceDocument
import com.gimlee.user.persistence.model.UserPresenceDocument.Companion.COLLECTION_NAME
import com.gimlee.user.persistence.model.UserPresenceDocument.Companion.FIELD_CUSTOM_STATUS
import com.gimlee.user.persistence.model.UserPresenceDocument.Companion.FIELD_LAST_SEEN_AT
import com.gimlee.user.persistence.model.UserPresenceDocument.Companion.FIELD_STATUS
import com.gimlee.user.persistence.model.UserPresenceDocument.Companion.FIELD_USER_ID
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Component

@Component
class UserPresenceRepository(
    mongoDatabase: MongoDatabase
) {
    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun findByUserId(userId: String): UserPresenceDocument? {
        val filter = Filters.eq(FIELD_USER_ID, ObjectId(userId))
        val doc = collection.find(filter).firstOrNull() ?: return null

        return UserPresenceDocument(
            userId = doc.getObjectId(FIELD_USER_ID),
            lastSeenAt = doc.getLong(FIELD_LAST_SEEN_AT),
            status = doc.getString(FIELD_STATUS),
            customStatus = doc.getString(FIELD_CUSTOM_STATUS)
        )
    }

    fun save(document: UserPresenceDocument) {
        val doc = Document()
            .append(FIELD_USER_ID, document.userId)
            .append(FIELD_LAST_SEEN_AT, document.lastSeenAt)
            .append(FIELD_STATUS, document.status)
            .append(FIELD_CUSTOM_STATUS, document.customStatus)

        val filter = Filters.eq(FIELD_USER_ID, document.userId)
        collection.replaceOne(filter, doc, ReplaceOptions().upsert(true))
    }

    fun bulkUpdateLastSeen(updates: Map<String, Long>) {
        if (updates.isEmpty()) return

        val models = updates.map { (userId, lastSeenAt) ->
            UpdateOneModel<Document>(
                Filters.eq(FIELD_USER_ID, ObjectId(userId)),
                Updates.combine(
                    Updates.set(FIELD_LAST_SEEN_AT, lastSeenAt),
                    Updates.setOnInsert(FIELD_STATUS, UserPresenceStatus.ONLINE.shortName)
                ),
                UpdateOptions().upsert(true)
            )
        }

        collection.bulkWrite(models)
    }
}

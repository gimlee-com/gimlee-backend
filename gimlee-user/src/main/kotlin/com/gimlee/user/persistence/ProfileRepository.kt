package com.gimlee.user.persistence

import com.gimlee.user.persistence.model.UserProfileDocument
import com.gimlee.user.persistence.model.UserProfileDocument.Companion.COLLECTION_NAME
import com.gimlee.user.persistence.model.UserProfileDocument.Companion.FIELD_AVATAR_URL
import com.gimlee.user.persistence.model.UserProfileDocument.Companion.FIELD_UPDATED_AT
import com.gimlee.user.persistence.model.UserProfileDocument.Companion.FIELD_USER_ID
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class ProfileRepository(private val mongoDatabase: MongoDatabase) {

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(document: UserProfileDocument) {
        val bson = Document()
            .append(FIELD_USER_ID, document.userId)
            .append(FIELD_AVATAR_URL, document.avatarUrl)
            .append(FIELD_UPDATED_AT, document.updatedAt)

        val filter = Filters.eq(FIELD_USER_ID, document.userId)
        val options = ReplaceOptions().upsert(true)
        collection.replaceOne(filter, bson, options)
    }

    fun findByUserId(userId: ObjectId): UserProfileDocument? {
        val filter = Filters.eq(FIELD_USER_ID, userId)
        val doc = collection.find(filter).firstOrNull() ?: return null

        return UserProfileDocument(
            userId = doc.getObjectId(FIELD_USER_ID),
            avatarUrl = doc.getString(FIELD_AVATAR_URL),
            updatedAt = doc.getLong(FIELD_UPDATED_AT)
        )
    }
}

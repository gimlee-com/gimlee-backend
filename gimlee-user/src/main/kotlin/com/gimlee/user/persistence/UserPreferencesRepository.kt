package com.gimlee.user.persistence

import com.gimlee.user.persistence.model.UserPreferencesDocument
import com.gimlee.user.persistence.model.UserPreferencesDocument.Companion.COLLECTION_NAME
import com.gimlee.user.persistence.model.UserPreferencesDocument.Companion.FIELD_USER_ID
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class UserPreferencesRepository(private val mongoDatabase: MongoDatabase) {

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(document: UserPreferencesDocument) {
        val bson = Document()
            .append(FIELD_USER_ID, document.userId)
            .append(UserPreferencesDocument.FIELD_LANGUAGE, document.language)
            .append(UserPreferencesDocument.FIELD_PREFERRED_CURRENCY, document.preferredCurrency)
            .append(UserPreferencesDocument.FIELD_COUNTRY_OF_RESIDENCE, document.countryOfResidence)

        val filter = Filters.eq(FIELD_USER_ID, document.userId)
        val options = ReplaceOptions().upsert(true)
        collection.replaceOne(filter, bson, options)
    }

    fun findByUserId(userId: ObjectId): UserPreferencesDocument? {
        val filter = Filters.eq(FIELD_USER_ID, userId)
        val doc = collection.find(filter).firstOrNull() ?: return null
        
        return UserPreferencesDocument(
            userId = doc.getObjectId(FIELD_USER_ID),
            language = doc.getString(UserPreferencesDocument.FIELD_LANGUAGE),
            preferredCurrency = doc.getString(UserPreferencesDocument.FIELD_PREFERRED_CURRENCY),
            countryOfResidence = doc.getString(UserPreferencesDocument.FIELD_COUNTRY_OF_RESIDENCE)
        )
    }

    fun setCountryOfResidence(userId: ObjectId, countryOfResidence: String) {
        val filter = Filters.eq(FIELD_USER_ID, userId)
        val update = Updates.combine(
            Updates.set(UserPreferencesDocument.FIELD_COUNTRY_OF_RESIDENCE, countryOfResidence),
            Updates.setOnInsert(UserPreferencesDocument.FIELD_LANGUAGE, "en-US")
        )
        collection.updateOne(filter, update, UpdateOptions().upsert(true))
    }
}

package com.gimlee.common.persistence

import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.springframework.stereotype.Repository

@Repository
class SystemMetadataRepository(mongoDatabase: MongoDatabase) {
    companion object {
        private const val COLLECTION_NAME = "gimlee-metadata"
        private const val FIELD_ID = "_id"
        private const val FIELD_VALUE = "v"
    }

    private val collection = mongoDatabase.getCollection(COLLECTION_NAME)

    fun getTimestamp(key: String): Long? =
        collection.find(Filters.eq(FIELD_ID, key)).firstOrNull()?.getLong(FIELD_VALUE)

    fun setTimestamp(key: String, timestamp: Long) {
        collection.updateOne(
            Filters.eq(FIELD_ID, key),
            Updates.set(FIELD_VALUE, timestamp),
            UpdateOptions().upsert(true)
        )
    }
}

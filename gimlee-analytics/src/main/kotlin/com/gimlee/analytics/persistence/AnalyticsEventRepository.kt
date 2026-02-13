package com.gimlee.analytics.persistence

import com.gimlee.analytics.persistence.model.AnalyticsEventDocument
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class AnalyticsEventRepository(mongoDatabase: MongoDatabase) {

    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-analytics"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-events"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(event: AnalyticsEventDocument) {
        collection.insertOne(mapToDocument(event))
    }

    private fun mapToDocument(event: AnalyticsEventDocument): Document {
        return Document()
            .append(AnalyticsEventDocument.FIELD_ID, event.id)
            .append(AnalyticsEventDocument.FIELD_TYPE, event.type)
            .append(AnalyticsEventDocument.FIELD_TARGET_ID, event.targetId)
            .append(AnalyticsEventDocument.FIELD_TIMESTAMP, event.timestampMicros)
            .append(AnalyticsEventDocument.FIELD_SAMPLE_RATE, event.sampleRate)
            .append(AnalyticsEventDocument.FIELD_USER_ID, event.userId)
            .append(AnalyticsEventDocument.FIELD_CLIENT_ID, event.clientId)
            .append(AnalyticsEventDocument.FIELD_BOT_SCORE, event.botScore)
            .append(AnalyticsEventDocument.FIELD_USER_AGENT, event.userAgent)
            .append(AnalyticsEventDocument.FIELD_REFERRER, event.referrer)
            .append(AnalyticsEventDocument.FIELD_METADATA, event.metadata?.let { Document(it) })
    }

    fun clear() {
        collection.deleteMany(Document())
    }
}

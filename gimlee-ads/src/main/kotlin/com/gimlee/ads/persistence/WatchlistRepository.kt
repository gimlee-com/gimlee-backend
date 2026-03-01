package com.gimlee.ads.persistence

import com.gimlee.ads.persistence.model.WatchlistDocument
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import java.time.Instant
import com.gimlee.common.toMicros

@Repository
class WatchlistRepository(private val mongoDatabase: MongoDatabase) {

    companion object {
        const val COLLECTION_NAME = "gimlee-ads-watchlist"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun add(userId: ObjectId, adId: ObjectId): Boolean {
        val filter = Filters.and(
            Filters.eq(WatchlistDocument.FIELD_USER_ID, userId),
            Filters.eq(WatchlistDocument.FIELD_AD_ID, adId)
        )
        
        // Use upsert to avoid duplicates
        val update = Updates.setOnInsert(WatchlistDocument.FIELD_CREATED_AT, Instant.now().toMicros())
        
        val result = collection.updateOne(filter, update, UpdateOptions().upsert(true))
        return result.matchedCount == 0L
    }

    fun remove(userId: ObjectId, adId: ObjectId): Boolean {
        val filter = Filters.and(
            Filters.eq(WatchlistDocument.FIELD_USER_ID, userId),
            Filters.eq(WatchlistDocument.FIELD_AD_ID, adId)
        )
        val result = collection.deleteOne(filter)
        return result.deletedCount > 0
    }

    fun findAllByUserId(userId: ObjectId): List<WatchlistDocument> {
        val filter = Filters.eq(WatchlistDocument.FIELD_USER_ID, userId)
        return collection.find(filter)
            .map { mapToWatchlistDocument(it) }
            .toList()
    }
    
    fun exists(userId: ObjectId, adId: ObjectId): Boolean {
        val filter = Filters.and(
            Filters.eq(WatchlistDocument.FIELD_USER_ID, userId),
            Filters.eq(WatchlistDocument.FIELD_AD_ID, adId)
        )
        return collection.countDocuments(filter) > 0
    }

    fun findWatchedAdIds(userId: ObjectId, adIds: List<ObjectId>): Set<ObjectId> {
        val filter = Filters.and(
            Filters.eq(WatchlistDocument.FIELD_USER_ID, userId),
            Filters.`in`(WatchlistDocument.FIELD_AD_ID, adIds)
        )
        return collection.find(filter)
            .map { it.getObjectId(WatchlistDocument.FIELD_AD_ID) }
            .toSet()
    }

    fun countByAdId(adId: ObjectId): Long {
        val filter = Filters.eq(WatchlistDocument.FIELD_AD_ID, adId)
        return collection.countDocuments(filter)
    }

    private fun mapToWatchlistDocument(doc: Document): WatchlistDocument {
        return WatchlistDocument(
            id = doc.getObjectId(WatchlistDocument.FIELD_ID),
            userId = doc.getObjectId(WatchlistDocument.FIELD_USER_ID),
            adId = doc.getObjectId(WatchlistDocument.FIELD_AD_ID),
            createdAt = doc.getLong(WatchlistDocument.FIELD_CREATED_AT) ?: 0L
        )
    }
}

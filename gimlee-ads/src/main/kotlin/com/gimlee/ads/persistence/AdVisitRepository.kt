package com.gimlee.ads.persistence

import com.gimlee.ads.persistence.model.AdVisitDocument
import com.gimlee.common.persistence.mongo.MongoExceptionUtils
import com.mongodb.MongoException
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.*
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import java.util.Date

@Repository
class AdVisitRepository(private val mongoDatabase: MongoDatabase) {

    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-ads"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-ad-visits"
        const val DEDUP_COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-ad-visit-dedup"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    private val dedupCollection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(DEDUP_COLLECTION_NAME)
    }

    fun recordVisit(adId: String, dateInt: Int, hash: Long) {
        val adObjectId = ObjectId(adId)
        val dedupId = "$adId|$dateInt|$hash"

        try {
            // Step 1: Try to record the unique visit in the dedup collection.
            // This collection handles deduplication across multiple application nodes.
            // Using a Date field for TTL cleanup.
            dedupCollection.insertOne(Document("_id", dedupId).append("exp", Date()))
        } catch (e: MongoException) {
            if (MongoExceptionUtils.isDuplicateKeyException(e)) {
                return // Already recorded today, skip.
            }
            throw e
        }

        // Step 2: If we successfully inserted into dedup, increment the daily count.
        // This avoids large arrays and expensive $ne checks on growing documents.
        val filter = Filters.and(
            Filters.eq(AdVisitDocument.FIELD_AD_ID, adObjectId),
            Filters.eq(AdVisitDocument.FIELD_DATE, dateInt)
        )

        collection.updateOne(
            filter,
            Updates.combine(
                Updates.setOnInsert(AdVisitDocument.FIELD_AD_ID, adObjectId),
                Updates.setOnInsert(AdVisitDocument.FIELD_DATE, dateInt),
                Updates.setOnInsert(AdVisitDocument.FIELD_EXPIRATION_DATE, Date()),
                Updates.inc(AdVisitDocument.FIELD_COUNT, 1)
            ),
            UpdateOptions().upsert(true)
        )
    }

    fun getVisitCount(adId: String, startDateInt: Int, endDateInt: Int): Long {
        val filter = Filters.and(
            Filters.eq(AdVisitDocument.FIELD_AD_ID, ObjectId(adId)),
            Filters.gte(AdVisitDocument.FIELD_DATE, startDateInt),
            Filters.lte(AdVisitDocument.FIELD_DATE, endDateInt)
        )
        val docs = collection.find(filter).toList()
        return docs.sumOf { (it.get(AdVisitDocument.FIELD_COUNT) as? Number)?.toLong() ?: 0L }
    }

    fun findDailyVisits(adId: String, startDateInt: Int, endDateInt: Int): List<AdVisitDocument> {
        val aid = ObjectId(adId)
        val filter = Filters.and(
            Filters.eq(AdVisitDocument.FIELD_AD_ID, aid),
            Filters.gte(AdVisitDocument.FIELD_DATE, startDateInt),
            Filters.lte(AdVisitDocument.FIELD_DATE, endDateInt)
        )
        return collection.find(filter)
            .sort(Sorts.ascending(AdVisitDocument.FIELD_DATE))
            .map { mapToAdVisitDocument(it) }
            .toList()
    }

    private fun mapToAdVisitDocument(doc: Document): AdVisitDocument {
        return AdVisitDocument(
            id = doc.getObjectId(AdVisitDocument.FIELD_ID),
            adId = doc.getObjectId(AdVisitDocument.FIELD_AD_ID),
            dateInt = doc.getInteger(AdVisitDocument.FIELD_DATE) ?: 0,
            count = (doc.get(AdVisitDocument.FIELD_COUNT) as? Number)?.toInt() ?: 0,
            expirationDate = doc.getDate(AdVisitDocument.FIELD_EXPIRATION_DATE)
        )
    }
    
    fun clear() {
        collection.deleteMany(Document())
        dedupCollection.deleteMany(Document())
    }
}

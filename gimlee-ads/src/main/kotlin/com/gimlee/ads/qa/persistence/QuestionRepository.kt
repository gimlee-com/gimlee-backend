package com.gimlee.ads.qa.persistence

import com.gimlee.ads.qa.persistence.model.QuestionDocument
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_AD_ID
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_AUTHOR_ID
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_CREATED_AT
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_ID
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_IS_PINNED
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_STATUS
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_TEXT
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_UPDATED_AT
import com.gimlee.ads.qa.persistence.model.QuestionDocument.Companion.FIELD_UPVOTE_COUNT
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class QuestionRepository(private val mongoDatabase: MongoDatabase) {

    companion object {
        const val COLLECTION_NAME = "gimlee-ads-questions"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(doc: QuestionDocument): QuestionDocument {
        val id = doc.id ?: ObjectId()
        val bson = Document()
            .append(FIELD_ID, id)
            .append(FIELD_AD_ID, doc.adId)
            .append(FIELD_AUTHOR_ID, doc.authorId)
            .append(FIELD_TEXT, doc.text)
            .append(FIELD_UPVOTE_COUNT, doc.upvoteCount)
            .append(FIELD_IS_PINNED, doc.isPinned)
            .append(FIELD_STATUS, doc.status)
            .append(FIELD_CREATED_AT, doc.createdAt)
            .append(FIELD_UPDATED_AT, doc.updatedAt)
        collection.insertOne(bson)
        return doc.copy(id = id)
    }

    fun findById(id: ObjectId): QuestionDocument? {
        return collection.find(Filters.eq(FIELD_ID, id))
            .firstOrNull()?.let { mapToDocument(it) }
    }

    fun findByAdIdAndStatuses(
        adId: ObjectId,
        statuses: List<String>,
        sort: org.bson.conversions.Bson,
        pageable: Pageable
    ): Page<QuestionDocument> {
        val filter = Filters.and(
            Filters.eq(FIELD_AD_ID, adId),
            Filters.`in`(FIELD_STATUS, statuses)
        )
        val total = collection.countDocuments(filter)
        val docs = collection.find(filter)
            .sort(sort)
            .skip(pageable.pageNumber * pageable.pageSize)
            .limit(pageable.pageSize)
            .map { mapToDocument(it) }
            .toList()
        return PageImpl(docs, pageable, total)
    }

    fun findUnansweredByAdId(adId: ObjectId, pageable: Pageable): Page<QuestionDocument> {
        val filter = Filters.and(
            Filters.eq(FIELD_AD_ID, adId),
            Filters.eq(FIELD_STATUS, "P")
        )
        val total = collection.countDocuments(filter)
        val docs = collection.find(filter)
            .sort(Sorts.descending(FIELD_CREATED_AT))
            .skip(pageable.pageNumber * pageable.pageSize)
            .limit(pageable.pageSize)
            .map { mapToDocument(it) }
            .toList()
        return PageImpl(docs, pageable, total)
    }

    fun findByAdIdAndAuthorId(adId: ObjectId, authorId: ObjectId, status: String): List<QuestionDocument> {
        val filter = Filters.and(
            Filters.eq(FIELD_AD_ID, adId),
            Filters.eq(FIELD_AUTHOR_ID, authorId),
            Filters.eq(FIELD_STATUS, status)
        )
        return collection.find(filter)
            .sort(Sorts.descending(FIELD_CREATED_AT))
            .map { mapToDocument(it) }
            .toList()
    }

    fun countUnansweredByAuthorAndAd(authorId: ObjectId, adId: ObjectId): Long {
        val filter = Filters.and(
            Filters.eq(FIELD_AUTHOR_ID, authorId),
            Filters.eq(FIELD_AD_ID, adId),
            Filters.eq(FIELD_STATUS, "P")
        )
        return collection.countDocuments(filter)
    }

    fun findLatestByAuthorAndAd(authorId: ObjectId, adId: ObjectId): QuestionDocument? {
        val filter = Filters.and(
            Filters.eq(FIELD_AUTHOR_ID, authorId),
            Filters.eq(FIELD_AD_ID, adId)
        )
        return collection.find(filter)
            .sort(Sorts.descending(FIELD_CREATED_AT))
            .firstOrNull()?.let { mapToDocument(it) }
    }

    fun findPinnedByAdId(adId: ObjectId): List<QuestionDocument> {
        val filter = Filters.and(
            Filters.eq(FIELD_AD_ID, adId),
            Filters.eq(FIELD_IS_PINNED, true)
        )
        return collection.find(filter)
            .map { mapToDocument(it) }
            .toList()
    }

    fun countByAdId(adId: ObjectId): Long {
        return collection.countDocuments(Filters.eq(FIELD_AD_ID, adId))
    }

    fun countByAdIdAndStatus(adId: ObjectId, status: String): Long {
        val filter = Filters.and(
            Filters.eq(FIELD_AD_ID, adId),
            Filters.eq(FIELD_STATUS, status)
        )
        return collection.countDocuments(filter)
    }

    fun incrementUpvoteCount(questionId: ObjectId, delta: Int) {
        collection.updateOne(
            Filters.eq(FIELD_ID, questionId),
            Updates.inc(FIELD_UPVOTE_COUNT, delta)
        )
    }

    fun updateStatus(questionId: ObjectId, newStatus: String, updatedAt: Long) {
        collection.updateOne(
            Filters.eq(FIELD_ID, questionId),
            Updates.combine(
                Updates.set(FIELD_STATUS, newStatus),
                Updates.set(FIELD_UPDATED_AT, updatedAt)
            )
        )
    }

    fun updatePinned(questionId: ObjectId, isPinned: Boolean, updatedAt: Long) {
        collection.updateOne(
            Filters.eq(FIELD_ID, questionId),
            Updates.combine(
                Updates.set(FIELD_IS_PINNED, isPinned),
                Updates.set(FIELD_UPDATED_AT, updatedAt)
            )
        )
    }

    fun deleteByAdId(adId: ObjectId) {
        collection.deleteMany(Filters.eq(FIELD_AD_ID, adId))
    }

    private fun mapToDocument(doc: Document): QuestionDocument {
        return QuestionDocument(
            id = doc.getObjectId(FIELD_ID),
            adId = doc.getObjectId(FIELD_AD_ID),
            authorId = doc.getObjectId(FIELD_AUTHOR_ID),
            text = doc.getString(FIELD_TEXT),
            upvoteCount = doc.getInteger(FIELD_UPVOTE_COUNT, 0),
            isPinned = doc.getBoolean(FIELD_IS_PINNED, false),
            status = doc.getString(FIELD_STATUS),
            createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L,
            updatedAt = doc.getLong(FIELD_UPDATED_AT) ?: 0L
        )
    }
}

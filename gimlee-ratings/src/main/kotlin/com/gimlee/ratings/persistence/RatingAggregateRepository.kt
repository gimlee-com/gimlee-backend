package com.gimlee.ratings.persistence

import com.gimlee.ratings.persistence.model.RatingAggregateDocument
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.COLLECTION_NAME
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_COUNT
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_DIST
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_ID
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_LAST_RATING_AT
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_RATEE_ID
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_REP_KIND
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_SUBJECT_KIND
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_SUM
import com.gimlee.ratings.persistence.model.RatingAggregateDocument.Companion.FIELD_UPDATED_AT
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class RatingAggregateRepository(private val mongoTemplate: MongoTemplate) {

    fun findByRateeAndRepKind(rateeId: String, repKind: String): RatingAggregateDocument? {
        val query = Query(
            Criteria.where(FIELD_ID).`is`(RatingAggregateDocument.compositeId(rateeId, repKind))
        )
        return mongoTemplate.findOne(query, Document::class.java, COLLECTION_NAME)
            ?.let { fromDocument(it) }
    }

    fun upsertOnPublish(
        rateeId: String,
        repKind: String,
        subjectKind: String,
        score: Int,
        ratingAt: Long,
        updatedAt: Long
    ) {
        val id = RatingAggregateDocument.compositeId(rateeId, repKind)
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .inc(FIELD_COUNT, 1)
            .inc(FIELD_SUM, score.toLong())
            .inc("$FIELD_DIST.$score", 1)
            .setOnInsert(FIELD_RATEE_ID, rateeId)
            .setOnInsert(FIELD_REP_KIND, repKind)
            .setOnInsert(FIELD_SUBJECT_KIND, subjectKind)
            .set(FIELD_LAST_RATING_AT, ratingAt)
            .set(FIELD_UPDATED_AT, updatedAt)
        mongoTemplate.upsert(query, update, COLLECTION_NAME)
    }

    fun updateOnHide(
        rateeId: String,
        repKind: String,
        score: Int,
        updatedAt: Long
    ) {
        val id = RatingAggregateDocument.compositeId(rateeId, repKind)
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .inc(FIELD_COUNT, -1)
            .inc(FIELD_SUM, -score.toLong())
            .inc("$FIELD_DIST.$score", -1)
            .set(FIELD_UPDATED_AT, updatedAt)
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
    }

    fun updateOnRestore(
        rateeId: String,
        repKind: String,
        score: Int,
        ratingAt: Long,
        updatedAt: Long
    ) {
        val id = RatingAggregateDocument.compositeId(rateeId, repKind)
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .inc(FIELD_COUNT, 1)
            .inc(FIELD_SUM, score.toLong())
            .inc("$FIELD_DIST.$score", 1)
            .set(FIELD_LAST_RATING_AT, ratingAt)
            .set(FIELD_UPDATED_AT, updatedAt)
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
    }

    private fun fromDocument(doc: Document): RatingAggregateDocument {
        @Suppress("UNCHECKED_CAST")
        val dist = (doc.get(FIELD_DIST, Document::class.java) ?: Document())
            .entries.associate { it.key to (it.value as Number).toInt() }
        return RatingAggregateDocument(
            rateeId = doc.getString(FIELD_RATEE_ID),
            repKind = doc.getString(FIELD_REP_KIND),
            subjectKind = doc.getString(FIELD_SUBJECT_KIND),
            count = doc.getInteger(FIELD_COUNT, 0),
            sum = doc.getLong(FIELD_SUM) ?: 0L,
            dist = dist,
            lastRatingAt = doc.getLong(FIELD_LAST_RATING_AT),
            updatedAt = doc.getLong(FIELD_UPDATED_AT)
        )
    }
}

package com.gimlee.ratings.persistence

import com.gimlee.common.persistence.mongo.MongoExceptionUtils
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.COLLECTION_NAME
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_ACTIVE_FROM
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_CONTEXT_ID
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_CONTEXT_TYPE
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_CREATED_AT
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_EXPIRES_AT
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_ID
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_RATER_ID
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_RATEE_ID
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_RATING_ID
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_REP_KIND
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_SNAPSHOT
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument.Companion.FIELD_STATUS
import com.gimlee.ratings.persistence.model.RatingSnapshotDocument
import com.gimlee.ratings.persistence.model.RatingSnapshotItemDocument
import org.bson.Document
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class RatingEligibilityRepository(private val mongoTemplate: MongoTemplate) {

    fun save(doc: RatingEligibilityDocument): RatingEligibilityDocument? {
        return try {
            val bson = Document()
                .append(FIELD_ID, doc.id)
                .append(FIELD_CONTEXT_TYPE, doc.contextType)
                .append(FIELD_CONTEXT_ID, doc.contextId)
                .append(FIELD_RATER_ID, doc.raterId)
                .append(FIELD_RATEE_ID, doc.rateeId)
                .append(FIELD_REP_KIND, doc.repKind)
                .append(FIELD_SNAPSHOT, snapshotToDocument(doc.snapshot))
                .append(FIELD_STATUS, doc.status)
                .append(FIELD_RATING_ID, doc.ratingId)
                .append(FIELD_ACTIVE_FROM, doc.activeFrom)
                .append(FIELD_EXPIRES_AT, doc.expiresAt)
                .append(FIELD_CREATED_AT, doc.createdAt)
            mongoTemplate.insert(bson, COLLECTION_NAME)
            doc
        } catch (e: Exception) {
            if (MongoExceptionUtils.isDuplicateKeyException(e)) null else throw e
        }
    }

    fun findById(id: String): RatingEligibilityDocument? {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        return mongoTemplate.findOne(query, Document::class.java, COLLECTION_NAME)
            ?.let { fromDocument(it) }
    }

    fun findPendingByRaterPaginated(raterId: String, pageable: Pageable): Page<RatingEligibilityDocument> {
        val query = Query(
            Criteria.where(FIELD_RATER_ID).`is`(raterId)
                .and(FIELD_STATUS).`is`("PND")
        )
        val total = mongoTemplate.count(query, COLLECTION_NAME)
        query.with(Sort.by(Sort.Direction.ASC, FIELD_ACTIVE_FROM)).with(pageable)
        val docs = mongoTemplate.find(query, Document::class.java, COLLECTION_NAME)
            .map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun findPendingByContextAndRater(contextId: String, raterId: String, contextType: String): RatingEligibilityDocument? {
        val query = Query(
            Criteria.where(FIELD_CONTEXT_ID).`is`(contextId)
                .and(FIELD_RATER_ID).`is`(raterId)
                .and(FIELD_CONTEXT_TYPE).`is`(contextType)
                .and(FIELD_STATUS).`is`("PND")
        )
        return mongoTemplate.findOne(query, Document::class.java, COLLECTION_NAME)
            ?.let { fromDocument(it) }
    }

    fun consumeEligibility(id: String, ratingId: String): Boolean {
        val query = Query(
            Criteria.where(FIELD_ID).`is`(id)
                .and(FIELD_STATUS).`is`("PND")
        )
        val update = Update()
            .set(FIELD_STATUS, "CSD")
            .set(FIELD_RATING_ID, ratingId)
        return mongoTemplate.updateFirst(query, update, COLLECTION_NAME).modifiedCount > 0
    }

    fun findExpiredPending(now: Long, limit: Int): List<RatingEligibilityDocument> {
        val query = Query(
            Criteria.where(FIELD_STATUS).`is`("PND")
                .and(FIELD_EXPIRES_AT).lt(now)
        ).limit(limit)
        return mongoTemplate.find(query, Document::class.java, COLLECTION_NAME)
            .map { fromDocument(it) }
    }

    fun deleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        val query = Query(Criteria.where(FIELD_ID).`in`(ids))
        mongoTemplate.remove(query, COLLECTION_NAME)
    }

    private fun snapshotToDocument(snap: RatingSnapshotDocument): Document =
        Document()
            .append(RatingSnapshotDocument.FIELD_REF_TYPE, snap.refType)
            .append(RatingSnapshotDocument.FIELD_ITEMS, snap.items.map { item ->
                Document()
                    .append(RatingSnapshotDocument.FIELD_ITEM_AD_ID, item.adId)
                    .append(RatingSnapshotDocument.FIELD_ITEM_NAME, item.name)
                    .append(RatingSnapshotDocument.FIELD_ITEM_QUANTITY, item.quantity)
                    .append(RatingSnapshotDocument.FIELD_ITEM_UNIT_PRICE, item.unitPrice)
                    .append(RatingSnapshotDocument.FIELD_ITEM_CURRENCY, item.currency)
                    .append(RatingSnapshotDocument.FIELD_ITEM_THUMB_PATH, item.thumbPath)
            })

    @Suppress("UNCHECKED_CAST")
    private fun fromDocument(doc: Document): RatingEligibilityDocument = RatingEligibilityDocument(
        id = doc.getString(FIELD_ID),
        contextType = doc.getString(FIELD_CONTEXT_TYPE),
        contextId = doc.getString(FIELD_CONTEXT_ID),
        raterId = doc.getString(FIELD_RATER_ID),
        rateeId = doc.getString(FIELD_RATEE_ID),
        repKind = doc.getString(FIELD_REP_KIND),
        snapshot = snapshotFromDocument(doc.get(FIELD_SNAPSHOT, Document::class.java)),
        status = doc.getString(FIELD_STATUS),
        ratingId = doc.getString(FIELD_RATING_ID),
        activeFrom = doc.getLong(FIELD_ACTIVE_FROM),
        expiresAt = doc.getLong(FIELD_EXPIRES_AT),
        createdAt = doc.getLong(FIELD_CREATED_AT)
    )

    private fun snapshotFromDocument(doc: Document): RatingSnapshotDocument = RatingSnapshotDocument(
        refType = doc.getString(RatingSnapshotDocument.FIELD_REF_TYPE),
        items = (doc.getList(RatingSnapshotDocument.FIELD_ITEMS, Document::class.java) ?: emptyList()).map { item ->
            RatingSnapshotItemDocument(
                adId = item.getString(RatingSnapshotDocument.FIELD_ITEM_AD_ID),
                name = item.getString(RatingSnapshotDocument.FIELD_ITEM_NAME),
                quantity = item.getInteger(RatingSnapshotDocument.FIELD_ITEM_QUANTITY),
                unitPrice = item.getString(RatingSnapshotDocument.FIELD_ITEM_UNIT_PRICE),
                currency = item.getString(RatingSnapshotDocument.FIELD_ITEM_CURRENCY),
                thumbPath = item.getString(RatingSnapshotDocument.FIELD_ITEM_THUMB_PATH)
            )
        }
    )
}

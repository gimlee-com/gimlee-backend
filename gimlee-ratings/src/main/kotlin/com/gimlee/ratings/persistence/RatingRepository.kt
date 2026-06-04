package com.gimlee.ratings.persistence

import com.gimlee.ratings.persistence.model.RatingDocument
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_BODY
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_CONTEXT_ID
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_CONTEXT_TYPE
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_CREATED_AT
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_EDITABLE_UNTIL
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_EDITED
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_HELPFUL_COUNT
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_ID
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_PHOTO_PATHS
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_PUBLISHED_AT
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_RATER_ID
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_RATEE_ID
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_REP_KIND
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_REPORT_COUNT
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_RESPONSE
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_SCORE
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_SNAPSHOT
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_STATUS
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_SUBJECT_KIND
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_SUPPLEMENTS
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_TITLE
import com.gimlee.ratings.persistence.model.RatingDocument.Companion.FIELD_UPDATED_AT
import com.gimlee.ratings.persistence.model.RatingResponseDocument
import com.gimlee.ratings.persistence.model.RatingSnapshotDocument
import com.gimlee.ratings.persistence.model.RatingSnapshotItemDocument
import com.gimlee.ratings.persistence.model.RatingSupplementDocument
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
class RatingRepository(private val mongoTemplate: MongoTemplate) {

    fun save(doc: RatingDocument): RatingDocument {
        val bson = Document()
            .append(FIELD_ID, doc.id)
            .append(FIELD_CONTEXT_TYPE, doc.contextType)
            .append(FIELD_CONTEXT_ID, doc.contextId)
            .append(FIELD_SUBJECT_KIND, doc.subjectKind)
            .append(FIELD_REP_KIND, doc.repKind)
            .append(FIELD_RATER_ID, doc.raterId)
            .append(FIELD_RATEE_ID, doc.rateeId)
            .append(FIELD_SCORE, doc.score)
            .append(FIELD_TITLE, doc.title)
            .append(FIELD_BODY, doc.body)
            .append(FIELD_PHOTO_PATHS, doc.photoPaths ?: emptyList<String>())
            .append(FIELD_SNAPSHOT, doc.snapshot?.let { snapshotToDocument(it) })
            .append(FIELD_STATUS, doc.status)
            .append(FIELD_EDITED, doc.edited)
            .append(FIELD_EDITABLE_UNTIL, doc.editableUntil)
            .append(FIELD_SUPPLEMENTS, doc.supplements?.map { supplementToDocument(it) } ?: emptyList<Document>())
            .append(FIELD_RESPONSE, doc.response?.let { responseToDocument(it) })
            .append(FIELD_HELPFUL_COUNT, doc.helpfulCount)
            .append(FIELD_REPORT_COUNT, doc.reportCount)
            .append(FIELD_CREATED_AT, doc.createdAt)
            .append(FIELD_UPDATED_AT, doc.updatedAt)
            .append(FIELD_PUBLISHED_AT, doc.publishedAt)
        mongoTemplate.insert(bson, RatingDocument.COLLECTION_NAME)
        return doc
    }

    fun findById(id: String): RatingDocument? {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        return mongoTemplate.findOne(query, Document::class.java, RatingDocument.COLLECTION_NAME)
            ?.let { fromDocument(it) }
    }

    fun findByContextAndRater(contextId: String, raterId: String, contextType: String): RatingDocument? {
        val query = Query(
            Criteria.where(FIELD_CONTEXT_ID).`is`(contextId)
                .and(FIELD_RATER_ID).`is`(raterId)
                .and(FIELD_CONTEXT_TYPE).`is`(contextType)
        )
        return mongoTemplate.findOne(query, Document::class.java, RatingDocument.COLLECTION_NAME)
            ?.let { fromDocument(it) }
    }

    fun findByRateePaginated(
        rateeId: String,
        repKind: String,
        pageable: Pageable
    ): Page<RatingDocument> {
        val criteria = Criteria.where(FIELD_RATEE_ID).`is`(rateeId)
            .and(FIELD_REP_KIND).`is`(repKind)
            .and(FIELD_STATUS).`is`("PUB")
        val query = Query(criteria)
        val total = mongoTemplate.count(query, RatingDocument.COLLECTION_NAME)
        query.with(Sort.by(Sort.Direction.DESC, FIELD_PUBLISHED_AT)).with(pageable)
        val docs = mongoTemplate.find(query, Document::class.java, RatingDocument.COLLECTION_NAME)
            .map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun findByRaterPaginated(raterId: String, pageable: Pageable): Page<RatingDocument> {
        val query = Query(Criteria.where(FIELD_RATER_ID).`is`(raterId))
        val total = mongoTemplate.count(query, RatingDocument.COLLECTION_NAME)
        query.with(Sort.by(Sort.Direction.DESC, FIELD_PUBLISHED_AT)).with(pageable)
        val docs = mongoTemplate.find(query, Document::class.java, RatingDocument.COLLECTION_NAME)
            .map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun findByRaterPublishedPaginated(raterId: String, pageable: Pageable): Page<RatingDocument> {
        val criteria = Criteria.where(FIELD_RATER_ID).`is`(raterId)
            .and(FIELD_STATUS).`is`("PUB")
        val query = Query(criteria)
        val total = mongoTemplate.count(query, RatingDocument.COLLECTION_NAME)
        query.with(Sort.by(Sort.Direction.DESC, FIELD_PUBLISHED_AT)).with(pageable)
        val docs = mongoTemplate.find(query, Document::class.java, RatingDocument.COLLECTION_NAME)
            .map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun findReportedRatings(pageable: Pageable): Page<RatingDocument> {
        val criteria = Criteria.where(FIELD_REPORT_COUNT).gt(0)
        val query = Query(criteria)
        val total = mongoTemplate.count(query, RatingDocument.COLLECTION_NAME)
        query.with(Sort.by(Sort.Direction.DESC, FIELD_REPORT_COUNT))
            .with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT))
            .with(pageable)
        val docs = mongoTemplate.find(query, Document::class.java, RatingDocument.COLLECTION_NAME)
            .map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun updateRating(
        id: String,
        score: Int,
        title: String?,
        body: String?,
        photoPaths: List<String>?,
        edited: Boolean,
        editableUntil: Long,
        updatedAt: Long
    ): Boolean {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .set(FIELD_SCORE, score)
            .set(FIELD_TITLE, title)
            .set(FIELD_BODY, body)
            .set(FIELD_PHOTO_PATHS, photoPaths)
            .set(FIELD_EDITED, edited)
            .set(FIELD_EDITABLE_UNTIL, editableUntil)
            .set(FIELD_UPDATED_AT, updatedAt)
        return mongoTemplate.updateFirst(query, update, RatingDocument.COLLECTION_NAME).modifiedCount > 0
    }

    fun addSupplement(id: String, supplement: RatingSupplementDocument, updatedAt: Long): Boolean {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .push(FIELD_SUPPLEMENTS, supplementToDocument(supplement))
            .set(FIELD_UPDATED_AT, updatedAt)
        return mongoTemplate.updateFirst(query, update, RatingDocument.COLLECTION_NAME).modifiedCount > 0
    }

    fun updateSupplement(
        ratingId: String,
        supplementId: String,
        body: String,
        editableUntil: Long,
        updatedAt: Long
    ): Boolean {
        val query = Query(
            Criteria.where(FIELD_ID).`is`(ratingId)
                .and("$FIELD_SUPPLEMENTS.${RatingSupplementDocument.FIELD_ID}").`is`(supplementId)
        )
        val update = Update()
            .set("$FIELD_SUPPLEMENTS.$.${RatingSupplementDocument.FIELD_BODY}", body)
            .set("$FIELD_SUPPLEMENTS.$.${RatingSupplementDocument.FIELD_EDITABLE_UNTIL}", editableUntil)
            .set(FIELD_UPDATED_AT, updatedAt)
        return mongoTemplate.updateFirst(query, update, RatingDocument.COLLECTION_NAME).modifiedCount > 0
    }

    fun setResponse(id: String, response: RatingResponseDocument, updatedAt: Long): Boolean {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .set(FIELD_RESPONSE, responseToDocument(response))
            .set(FIELD_UPDATED_AT, updatedAt)
        return mongoTemplate.updateFirst(query, update, RatingDocument.COLLECTION_NAME).modifiedCount > 0
    }

    fun updateStatus(id: String, status: String, publishedAt: Long?, updatedAt: Long): Boolean {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .set(FIELD_STATUS, status)
            .set(FIELD_PUBLISHED_AT, publishedAt)
            .set(FIELD_UPDATED_AT, updatedAt)
        return mongoTemplate.updateFirst(query, update, RatingDocument.COLLECTION_NAME).modifiedCount > 0
    }

    fun incrementReportCount(id: String): Boolean {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update().inc(FIELD_REPORT_COUNT, 1)
        return mongoTemplate.updateFirst(query, update, RatingDocument.COLLECTION_NAME).modifiedCount > 0
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

    private fun supplementToDocument(sup: RatingSupplementDocument): Document =
        Document()
            .append(RatingSupplementDocument.FIELD_ID, sup.id)
            .append(RatingSupplementDocument.FIELD_BODY, sup.body)
            .append(RatingSupplementDocument.FIELD_STATUS, sup.status)
            .append(RatingSupplementDocument.FIELD_EDITABLE_UNTIL, sup.editableUntil)
            .append(RatingSupplementDocument.FIELD_CREATED_AT, sup.createdAt)

    private fun responseToDocument(rsp: RatingResponseDocument): Document =
        Document()
            .append(RatingResponseDocument.FIELD_BODY, rsp.body)
            .append(RatingResponseDocument.FIELD_CREATED_AT, rsp.createdAt)
            .append(RatingResponseDocument.FIELD_UPDATED_AT, rsp.updatedAt)

    @Suppress("UNCHECKED_CAST")
    private fun fromDocument(doc: Document): RatingDocument = RatingDocument(
        id = doc.getString(FIELD_ID),
        contextType = doc.getString(FIELD_CONTEXT_TYPE),
        contextId = doc.getString(FIELD_CONTEXT_ID),
        subjectKind = doc.getString(FIELD_SUBJECT_KIND),
        repKind = doc.getString(FIELD_REP_KIND),
        raterId = doc.getString(FIELD_RATER_ID),
        rateeId = doc.getString(FIELD_RATEE_ID),
        score = doc.getInteger(FIELD_SCORE),
        title = doc.getString(FIELD_TITLE),
        body = doc.getString(FIELD_BODY),
        photoPaths = doc.getList(FIELD_PHOTO_PATHS, String::class.java),
        snapshot = doc.get(FIELD_SNAPSHOT, Document::class.java)?.let { snapshotFromDocument(it) },
        status = doc.getString(FIELD_STATUS),
        edited = doc.getBoolean(FIELD_EDITED, false),
        editableUntil = doc.getLong(FIELD_EDITABLE_UNTIL),
        supplements = doc.getList(FIELD_SUPPLEMENTS, Document::class.java)?.map { supplementFromDocument(it) },
        response = doc.get(FIELD_RESPONSE, Document::class.java)?.let { responseFromDocument(it) },
        helpfulCount = doc.getInteger(FIELD_HELPFUL_COUNT, 0),
        reportCount = doc.getInteger(FIELD_REPORT_COUNT, 0),
        createdAt = doc.getLong(FIELD_CREATED_AT),
        updatedAt = doc.getLong(FIELD_UPDATED_AT),
        publishedAt = doc.getLong(FIELD_PUBLISHED_AT)
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

    private fun supplementFromDocument(doc: Document): RatingSupplementDocument = RatingSupplementDocument(
        id = doc.getString(RatingSupplementDocument.FIELD_ID),
        body = doc.getString(RatingSupplementDocument.FIELD_BODY),
        status = doc.getString(RatingSupplementDocument.FIELD_STATUS),
        editableUntil = doc.getLong(RatingSupplementDocument.FIELD_EDITABLE_UNTIL),
        createdAt = doc.getLong(RatingSupplementDocument.FIELD_CREATED_AT)
    )

    private fun responseFromDocument(doc: Document): RatingResponseDocument = RatingResponseDocument(
        body = doc.getString(RatingResponseDocument.FIELD_BODY),
        createdAt = doc.getLong(RatingResponseDocument.FIELD_CREATED_AT),
        updatedAt = doc.getLong(RatingResponseDocument.FIELD_UPDATED_AT)
    )
}

package com.gimlee.notifications.persistence

import com.gimlee.notifications.persistence.model.NotificationDocument
import com.gimlee.notifications.persistence.model.NotificationDocument.Companion.FIELD_CATEGORY
import com.gimlee.notifications.persistence.model.NotificationDocument.Companion.FIELD_CREATED_AT
import com.gimlee.notifications.persistence.model.NotificationDocument.Companion.FIELD_ID
import com.gimlee.notifications.persistence.model.NotificationDocument.Companion.FIELD_READ
import com.gimlee.notifications.persistence.model.NotificationDocument.Companion.FIELD_USER_ID
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class NotificationRepository(private val mongoTemplate: MongoTemplate) {

    companion object {
        const val COLLECTION_NAME = "gimlee-notifications"
    }

    fun save(doc: NotificationDocument): NotificationDocument {
        mongoTemplate.insert(doc.toBson(), COLLECTION_NAME)
        return doc
    }

    fun findByUserId(
        userId: ObjectId,
        category: String?,
        limit: Int,
        beforeId: String?
    ): List<NotificationDocument> {
        val criteria = Criteria.where(FIELD_USER_ID).`is`(userId)
        category?.let { criteria.and(FIELD_CATEGORY).`is`(it) }
        beforeId?.let {
            val ref = mongoTemplate.findOne(
                Query(Criteria.where(FIELD_ID).`is`(it)),
                Document::class.java,
                COLLECTION_NAME
            )
            if (ref != null) {
                val refCa = ref.getLong(FIELD_CREATED_AT)
                criteria.orOperator(
                    Criteria.where(FIELD_CREATED_AT).lt(refCa),
                    Criteria.where(FIELD_CREATED_AT).`is`(refCa).and(FIELD_ID).lt(it)
                )
            }
        }

        val query = Query(criteria)
            .with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT).and(Sort.by(Sort.Direction.DESC, FIELD_ID)))
            .limit(limit)

        return mongoTemplate.find(query, Document::class.java, COLLECTION_NAME)
            .map { NotificationDocument.fromDocument(it) }
    }

    fun findById(id: String): NotificationDocument? {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        return mongoTemplate.findOne(query, Document::class.java, COLLECTION_NAME)
            ?.let { NotificationDocument.fromDocument(it) }
    }

    fun markAsRead(id: String, userId: ObjectId): Boolean {
        val query = Query(
            Criteria.where(FIELD_ID).`is`(id).and(FIELD_USER_ID).`is`(userId)
        )
        val update = Update.update(FIELD_READ, true)
        return mongoTemplate.updateFirst(query, update, COLLECTION_NAME).modifiedCount > 0
    }

    fun markAllAsRead(userId: ObjectId, category: String?): Long {
        val criteria = Criteria.where(FIELD_USER_ID).`is`(userId).and(FIELD_READ).`is`(false)
        category?.let { criteria.and(FIELD_CATEGORY).`is`(it) }

        val update = Update.update(FIELD_READ, true)
        return mongoTemplate.updateMulti(Query(criteria), update, COLLECTION_NAME).modifiedCount
    }

    fun countUnread(userId: ObjectId): Long {
        val query = Query(
            Criteria.where(FIELD_USER_ID).`is`(userId).and(FIELD_READ).`is`(false)
        )
        return mongoTemplate.count(query, COLLECTION_NAME)
    }

    fun deleteOlderThan(cutoffMicros: Long): Long {
        val query = Query(Criteria.where(FIELD_CREATED_AT).lt(cutoffMicros))
        return mongoTemplate.remove(query, COLLECTION_NAME).deletedCount
    }
}

package com.gimlee.ads.qa.persistence

import com.gimlee.ads.qa.persistence.model.QuestionUpvoteDocument
import com.gimlee.ads.qa.persistence.model.QuestionUpvoteDocument.Companion.FIELD_CREATED_AT
import com.gimlee.ads.qa.persistence.model.QuestionUpvoteDocument.Companion.FIELD_QUESTION_ID
import com.gimlee.ads.qa.persistence.model.QuestionUpvoteDocument.Companion.FIELD_USER_ID
import com.gimlee.common.toMicros
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class QuestionUpvoteRepository(private val mongoDatabase: MongoDatabase) {

    companion object {
        const val COLLECTION_NAME = "gimlee-ads-upvotes"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun upsert(questionId: ObjectId, userId: ObjectId): Boolean {
        val filter = Filters.and(
            Filters.eq(FIELD_QUESTION_ID, questionId),
            Filters.eq(FIELD_USER_ID, userId)
        )
        val update = Updates.setOnInsert(FIELD_CREATED_AT, Instant.now().toMicros())
        val result = collection.updateOne(filter, update, UpdateOptions().upsert(true))
        return result.matchedCount == 0L
    }

    fun delete(questionId: ObjectId, userId: ObjectId): Boolean {
        val filter = Filters.and(
            Filters.eq(FIELD_QUESTION_ID, questionId),
            Filters.eq(FIELD_USER_ID, userId)
        )
        return collection.deleteOne(filter).deletedCount > 0
    }

    fun exists(questionId: ObjectId, userId: ObjectId): Boolean {
        val filter = Filters.and(
            Filters.eq(FIELD_QUESTION_ID, questionId),
            Filters.eq(FIELD_USER_ID, userId)
        )
        return collection.countDocuments(filter) > 0
    }

    fun findUpvotedQuestionIds(userId: ObjectId, questionIds: List<ObjectId>): Set<ObjectId> {
        if (questionIds.isEmpty()) return emptySet()
        val filter = Filters.and(
            Filters.eq(FIELD_USER_ID, userId),
            Filters.`in`(FIELD_QUESTION_ID, questionIds)
        )
        return collection.find(filter)
            .map { it.getObjectId(FIELD_QUESTION_ID) }
            .toSet()
    }

    fun deleteByQuestionIds(questionIds: List<ObjectId>) {
        if (questionIds.isEmpty()) return
        collection.deleteMany(Filters.`in`(FIELD_QUESTION_ID, questionIds))
    }
}

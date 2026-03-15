package com.gimlee.ads.qa.persistence

import com.gimlee.ads.qa.persistence.model.AnswerDocument
import com.gimlee.ads.qa.persistence.model.AnswerDocument.Companion.FIELD_AUTHOR_ID
import com.gimlee.ads.qa.persistence.model.AnswerDocument.Companion.FIELD_CREATED_AT
import com.gimlee.ads.qa.persistence.model.AnswerDocument.Companion.FIELD_ID
import com.gimlee.ads.qa.persistence.model.AnswerDocument.Companion.FIELD_QUESTION_ID
import com.gimlee.ads.qa.persistence.model.AnswerDocument.Companion.FIELD_TEXT
import com.gimlee.ads.qa.persistence.model.AnswerDocument.Companion.FIELD_TYPE
import com.gimlee.ads.qa.persistence.model.AnswerDocument.Companion.FIELD_UPDATED_AT
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class AnswerRepository(private val mongoDatabase: MongoDatabase) {

    companion object {
        const val COLLECTION_NAME = "gimlee-ads-answers"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(doc: AnswerDocument): AnswerDocument {
        val id = doc.id ?: ObjectId()
        val bson = Document()
            .append(FIELD_ID, id)
            .append(FIELD_QUESTION_ID, doc.questionId)
            .append(FIELD_AUTHOR_ID, doc.authorId)
            .append(FIELD_TYPE, doc.type)
            .append(FIELD_TEXT, doc.text)
            .append(FIELD_CREATED_AT, doc.createdAt)
            .append(FIELD_UPDATED_AT, doc.updatedAt)
        collection.insertOne(bson)
        return doc.copy(id = id)
    }

    fun findById(id: ObjectId): AnswerDocument? {
        return collection.find(Filters.eq(FIELD_ID, id))
            .firstOrNull()?.let { mapToDocument(it) }
    }

    fun findByQuestionId(questionId: ObjectId): List<AnswerDocument> {
        return collection.find(Filters.eq(FIELD_QUESTION_ID, questionId))
            .sort(Sorts.ascending(FIELD_CREATED_AT))
            .map { mapToDocument(it) }
            .toList()
    }

    fun findByQuestionIds(questionIds: List<ObjectId>): Map<ObjectId, List<AnswerDocument>> {
        if (questionIds.isEmpty()) return emptyMap()
        return collection.find(Filters.`in`(FIELD_QUESTION_ID, questionIds))
            .sort(Sorts.ascending(FIELD_CREATED_AT))
            .map { mapToDocument(it) }
            .toList()
            .groupBy { it.questionId }
    }

    fun findSellerAnswerByQuestionId(questionId: ObjectId): AnswerDocument? {
        val filter = Filters.and(
            Filters.eq(FIELD_QUESTION_ID, questionId),
            Filters.eq(FIELD_TYPE, "S")
        )
        return collection.find(filter).firstOrNull()?.let { mapToDocument(it) }
    }

    fun countCommunityAnswersByQuestionId(questionId: ObjectId): Long {
        val filter = Filters.and(
            Filters.eq(FIELD_QUESTION_ID, questionId),
            Filters.eq(FIELD_TYPE, "C")
        )
        return collection.countDocuments(filter)
    }

    fun update(answerId: ObjectId, text: String, updatedAt: Long) {
        collection.updateOne(
            Filters.eq(FIELD_ID, answerId),
            Updates.combine(
                Updates.set(FIELD_TEXT, text),
                Updates.set(FIELD_UPDATED_AT, updatedAt)
            )
        )
    }

    fun deleteByQuestionIds(questionIds: List<ObjectId>) {
        if (questionIds.isEmpty()) return
        collection.deleteMany(Filters.`in`(FIELD_QUESTION_ID, questionIds))
    }

    private fun mapToDocument(doc: Document): AnswerDocument {
        return AnswerDocument(
            id = doc.getObjectId(FIELD_ID),
            questionId = doc.getObjectId(FIELD_QUESTION_ID),
            authorId = doc.getObjectId(FIELD_AUTHOR_ID),
            type = doc.getString(FIELD_TYPE),
            text = doc.getString(FIELD_TEXT),
            createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L,
            updatedAt = doc.getLong(FIELD_UPDATED_AT) ?: 0L
        )
    }
}

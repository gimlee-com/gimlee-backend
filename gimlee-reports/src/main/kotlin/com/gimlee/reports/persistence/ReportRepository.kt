package com.gimlee.reports.persistence

import com.gimlee.reports.persistence.model.ReportDocument
import com.gimlee.reports.persistence.model.ReportDocument.Companion.FIELD_CONTEXT_ID
import com.gimlee.reports.persistence.model.ReportDocument.Companion.FIELD_CREATED_AT
import com.gimlee.reports.persistence.model.ReportDocument.Companion.FIELD_ID
import com.gimlee.reports.persistence.model.ReportDocument.Companion.FIELD_REASON
import com.gimlee.reports.persistence.model.ReportDocument.Companion.FIELD_REPORTER_ID
import com.gimlee.reports.persistence.model.ReportDocument.Companion.FIELD_TARGET_ID
import com.gimlee.reports.persistence.model.ReportDocument.Companion.FIELD_TARGET_TYPE
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class ReportRepository(private val mongoDatabase: MongoDatabase) {

    companion object {
        const val COLLECTION_NAME = "gimlee-reports"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(doc: ReportDocument): ReportDocument {
        val id = doc.id ?: ObjectId()
        val bson = Document()
            .append(FIELD_ID, id)
            .append(FIELD_TARGET_ID, doc.targetId)
            .append(FIELD_TARGET_TYPE, doc.targetType)
            .append(FIELD_CONTEXT_ID, doc.contextId)
            .append(FIELD_REPORTER_ID, doc.reporterId)
            .append(FIELD_REASON, doc.reason)
            .append(FIELD_CREATED_AT, doc.createdAt)
        collection.insertOne(bson)
        return doc.copy(id = id)
    }

    fun deleteByTargetIds(targetIds: List<ObjectId>) {
        if (targetIds.isEmpty()) return
        collection.deleteMany(Filters.`in`(FIELD_TARGET_ID, targetIds))
    }
}

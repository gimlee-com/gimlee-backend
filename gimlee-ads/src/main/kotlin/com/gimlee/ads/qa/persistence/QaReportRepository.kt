package com.gimlee.ads.qa.persistence

import com.gimlee.ads.qa.persistence.model.QaReportDocument
import com.gimlee.ads.qa.persistence.model.QaReportDocument.Companion.FIELD_AD_ID
import com.gimlee.ads.qa.persistence.model.QaReportDocument.Companion.FIELD_CREATED_AT
import com.gimlee.ads.qa.persistence.model.QaReportDocument.Companion.FIELD_ID
import com.gimlee.ads.qa.persistence.model.QaReportDocument.Companion.FIELD_REASON
import com.gimlee.ads.qa.persistence.model.QaReportDocument.Companion.FIELD_REPORTER_ID
import com.gimlee.ads.qa.persistence.model.QaReportDocument.Companion.FIELD_TARGET_ID
import com.gimlee.ads.qa.persistence.model.QaReportDocument.Companion.FIELD_TARGET_TYPE
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class QaReportRepository(private val mongoDatabase: MongoDatabase) {

    companion object {
        const val COLLECTION_NAME = "gimlee-ads-reports"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(doc: QaReportDocument): QaReportDocument {
        val id = doc.id ?: ObjectId()
        val bson = Document()
            .append(FIELD_ID, id)
            .append(FIELD_TARGET_ID, doc.targetId)
            .append(FIELD_TARGET_TYPE, doc.targetType)
            .append(FIELD_AD_ID, doc.adId)
            .append(FIELD_REPORTER_ID, doc.reporterId)
            .append(FIELD_REASON, doc.reason)
            .append(FIELD_CREATED_AT, doc.createdAt)
        collection.insertOne(bson)
        return doc.copy(id = id)
    }

    fun existsByTargetAndReporter(targetId: ObjectId, reporterId: ObjectId): Boolean {
        val filter = Filters.and(
            Filters.eq(FIELD_TARGET_ID, targetId),
            Filters.eq(FIELD_REPORTER_ID, reporterId)
        )
        return collection.countDocuments(filter) > 0
    }

    fun deleteByTargetIds(targetIds: List<ObjectId>) {
        if (targetIds.isEmpty()) return
        collection.deleteMany(Filters.`in`(FIELD_TARGET_ID, targetIds))
    }
}

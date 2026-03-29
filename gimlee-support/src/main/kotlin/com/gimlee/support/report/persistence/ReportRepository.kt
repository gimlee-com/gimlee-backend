package com.gimlee.support.report.persistence

import com.gimlee.support.report.domain.model.*
import com.gimlee.support.report.persistence.model.ReportDocument
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_ASSIGNEE_ID
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_CONTEXT_ID
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_CREATED_AT
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_DESCRIPTION
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_ID
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_INTERNAL_NOTES
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_REASON
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_REPORTER_ID
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_RESOLUTION
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_RESOLVED_AT
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_RESOLVED_BY
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_SIBLING_COUNT
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_STATUS
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_TARGET_ID
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_TARGET_SNAPSHOT
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_TARGET_TITLE
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_TARGET_TYPE
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_TIMELINE
import com.gimlee.support.report.persistence.model.ReportDocument.Companion.FIELD_UPDATED_AT
import org.bson.Document
import org.bson.types.ObjectId
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
class ReportRepository(private val mongoTemplate: MongoTemplate) {

    companion object {
        const val COLLECTION_NAME = "gimlee-support-reports"
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
            .append(FIELD_DESCRIPTION, doc.description)
            .append(FIELD_STATUS, doc.status)
            .append(FIELD_TARGET_TITLE, doc.targetTitle)
            .append(FIELD_TARGET_SNAPSHOT, doc.targetSnapshot)
            .append(FIELD_ASSIGNEE_ID, doc.assigneeId)
            .append(FIELD_RESOLUTION, doc.resolution)
            .append(FIELD_RESOLVED_BY, doc.resolvedBy)
            .append(FIELD_RESOLVED_AT, doc.resolvedAt)
            .append(FIELD_INTERNAL_NOTES, doc.internalNotes)
            .append(FIELD_SIBLING_COUNT, doc.siblingCount)
            .append(FIELD_TIMELINE, doc.timeline)
            .append(FIELD_CREATED_AT, doc.createdAt)
            .append(FIELD_UPDATED_AT, doc.updatedAt)
        mongoTemplate.insert(bson, COLLECTION_NAME)
        return doc.copy(id = id)
    }

    fun findById(id: ObjectId): ReportDocument? {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        return mongoTemplate.findOne(query, Document::class.java, COLLECTION_NAME)?.let { fromDocument(it) }
    }

    fun existsByTargetAndReporter(targetId: ObjectId, reporterId: ObjectId): Boolean {
        val query = Query(Criteria.where(FIELD_TARGET_ID).`is`(targetId).and(FIELD_REPORTER_ID).`is`(reporterId))
        return mongoTemplate.count(query, COLLECTION_NAME) > 0
    }

    fun countByTarget(targetType: String, targetId: ObjectId): Long {
        val query = Query(Criteria.where(FIELD_TARGET_TYPE).`is`(targetType).and(FIELD_TARGET_ID).`is`(targetId))
        return mongoTemplate.count(query, COLLECTION_NAME)
    }

    fun updateSiblingCounts(targetType: String, targetId: ObjectId, count: Long) {
        val query = Query(Criteria.where(FIELD_TARGET_TYPE).`is`(targetType).and(FIELD_TARGET_ID).`is`(targetId))
        val update = Update().set(FIELD_SIBLING_COUNT, count)
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME)
    }

    fun findAllPaginated(
        status: ReportStatus?,
        targetType: ReportTargetType?,
        reason: ReportReason?,
        assigneeId: ObjectId?,
        search: String?,
        sortField: String?,
        sortDirection: String?,
        pageable: Pageable
    ): Page<ReportDocument> {
        val criteria = mutableListOf<Criteria>()
        status?.let { criteria.add(Criteria.where(FIELD_STATUS).`is`(it.shortName)) }
        targetType?.let { criteria.add(Criteria.where(FIELD_TARGET_TYPE).`is`(it.shortName)) }
        reason?.let { criteria.add(Criteria.where(FIELD_REASON).`is`(it.shortName)) }
        assigneeId?.let { criteria.add(Criteria.where(FIELD_ASSIGNEE_ID).`is`(it)) }
        if (!search.isNullOrBlank()) {
            val regex = ".*${Regex.escape(search)}.*"
            criteria.add(Criteria.where(FIELD_TARGET_TITLE).regex(regex, "i"))
        }

        val query = if (criteria.isEmpty()) Query() else Query(Criteria().andOperator(criteria))
        val total = mongoTemplate.count(query, COLLECTION_NAME)

        val sort = buildSort(sortField, sortDirection)
        query.with(sort).with(pageable)

        val docs = mongoTemplate.find(query, Document::class.java, COLLECTION_NAME).map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun findByTargetPaginated(
        targetType: ReportTargetType,
        targetId: ObjectId,
        excludeReportId: ObjectId?,
        pageable: Pageable
    ): Page<ReportDocument> {
        val criteria = mutableListOf(
            Criteria.where(FIELD_TARGET_TYPE).`is`(targetType.shortName),
            Criteria.where(FIELD_TARGET_ID).`is`(targetId)
        )
        excludeReportId?.let { criteria.add(Criteria.where(FIELD_ID).ne(it)) }

        val query = Query(Criteria().andOperator(criteria))
        val total = mongoTemplate.count(query, COLLECTION_NAME)
        query.with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT)).with(pageable)

        val docs = mongoTemplate.find(query, Document::class.java, COLLECTION_NAME).map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun findByReporterIdPaginated(reporterId: ObjectId, pageable: Pageable): Page<ReportDocument> {
        val query = Query(Criteria.where(FIELD_REPORTER_ID).`is`(reporterId))
        val total = mongoTemplate.count(query, COLLECTION_NAME)
        query.with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT)).with(pageable)

        val docs = mongoTemplate.find(query, Document::class.java, COLLECTION_NAME).map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun updateStatus(id: ObjectId, status: ReportStatus, updatedAt: Long) {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update().set(FIELD_STATUS, status.shortName).set(FIELD_UPDATED_AT, updatedAt)
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
    }

    fun updateAssignee(id: ObjectId, assigneeId: ObjectId, updatedAt: Long) {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update().set(FIELD_ASSIGNEE_ID, assigneeId).set(FIELD_UPDATED_AT, updatedAt)
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
    }

    fun resolve(
        id: ObjectId,
        resolution: ReportResolution,
        resolvedBy: ObjectId,
        resolvedAt: Long,
        internalNotes: String?,
        status: ReportStatus
    ) {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .set(FIELD_STATUS, status.shortName)
            .set(FIELD_RESOLUTION, resolution.shortName)
            .set(FIELD_RESOLVED_BY, resolvedBy)
            .set(FIELD_RESOLVED_AT, resolvedAt)
            .set(FIELD_INTERNAL_NOTES, internalNotes)
            .set(FIELD_UPDATED_AT, resolvedAt)
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
    }

    fun addTimelineEntry(id: ObjectId, entry: Document, updatedAt: Long) {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update().push(FIELD_TIMELINE, entry).set(FIELD_UPDATED_AT, updatedAt)
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
    }

    fun countByStatus(status: ReportStatus): Long {
        val query = Query(Criteria.where(FIELD_STATUS).`is`(status.shortName))
        return mongoTemplate.count(query, COLLECTION_NAME)
    }

    fun countByStatusIn(statuses: List<ReportStatus>): Long {
        val shortNames = statuses.map { it.shortName }
        val query = Query(Criteria.where(FIELD_STATUS).`in`(shortNames))
        return mongoTemplate.count(query, COLLECTION_NAME)
    }

    fun countResolvedSince(since: Long): Long {
        val query = Query(Criteria.where(FIELD_RESOLVED_AT).gte(since))
        return mongoTemplate.count(query, COLLECTION_NAME)
    }

    fun deleteByTargetIds(targetIds: List<ObjectId>) {
        if (targetIds.isEmpty()) return
        val query = Query(Criteria.where(FIELD_TARGET_ID).`in`(targetIds))
        mongoTemplate.remove(query, COLLECTION_NAME)
    }

    private fun buildSort(sortField: String?, sortDirection: String?): Sort {
        val direction = if (sortDirection == "ASC") Sort.Direction.ASC else Sort.Direction.DESC
        return when (sortField) {
            "createdAt" -> Sort.by(direction, FIELD_CREATED_AT)
            "updatedAt" -> Sort.by(direction, FIELD_UPDATED_AT)
            "siblingCount" -> Sort.by(direction, FIELD_SIBLING_COUNT).and(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT))
            else -> Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromDocument(doc: Document): ReportDocument = ReportDocument(
        id = doc.getObjectId(FIELD_ID),
        targetId = doc.getObjectId(FIELD_TARGET_ID),
        targetType = doc.getString(FIELD_TARGET_TYPE),
        contextId = doc.getObjectId(FIELD_CONTEXT_ID),
        reporterId = doc.getObjectId(FIELD_REPORTER_ID),
        reason = doc.getString(FIELD_REASON),
        description = doc.getString(FIELD_DESCRIPTION),
        status = doc.getString(FIELD_STATUS),
        targetTitle = doc.getString(FIELD_TARGET_TITLE),
        targetSnapshot = doc.get(FIELD_TARGET_SNAPSHOT, Document::class.java),
        assigneeId = doc.getObjectId(FIELD_ASSIGNEE_ID),
        resolution = doc.getString(FIELD_RESOLUTION),
        resolvedBy = doc.getObjectId(FIELD_RESOLVED_BY),
        resolvedAt = doc.getLong(FIELD_RESOLVED_AT),
        internalNotes = doc.getString(FIELD_INTERNAL_NOTES),
        siblingCount = doc.getLong(FIELD_SIBLING_COUNT) ?: 1L,
        timeline = (doc.getList(FIELD_TIMELINE, Document::class.java) ?: emptyList()),
        createdAt = doc.getLong(FIELD_CREATED_AT),
        updatedAt = doc.getLong(FIELD_UPDATED_AT)
    )
}

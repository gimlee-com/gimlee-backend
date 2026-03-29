package com.gimlee.support.ticket.persistence

import com.gimlee.support.ticket.domain.model.TicketCategory
import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus
import com.gimlee.support.ticket.persistence.model.TicketDocument
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_ASSIGNEE_ID
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_CATEGORY
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_CREATED_AT
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_CREATOR_ID
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_ID
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_LAST_MESSAGE_AT
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_MESSAGE_COUNT
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_PRIORITY
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_PRIORITY_ORDER
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_RELATED_REPORT_IDS
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_RESOLVED_AT
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_STATUS
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_SUBJECT
import com.gimlee.support.ticket.persistence.model.TicketDocument.Companion.FIELD_UPDATED_AT
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository

@Repository
class TicketRepository(private val mongoTemplate: MongoTemplate) {

    companion object {
        const val COLLECTION_NAME = "gimlee-support-tickets"
    }

    fun save(doc: TicketDocument): TicketDocument {
        val id = doc.id ?: ObjectId()
        val bson = Document()
            .append(FIELD_ID, id)
            .append(FIELD_SUBJECT, doc.subject)
            .append(FIELD_CATEGORY, doc.category)
            .append(FIELD_STATUS, doc.status)
            .append(FIELD_PRIORITY, doc.priority)
            .append(FIELD_PRIORITY_ORDER, doc.priorityOrder)
            .append(FIELD_CREATOR_ID, doc.creatorId)
            .append(FIELD_ASSIGNEE_ID, doc.assigneeId)
            .append(FIELD_RELATED_REPORT_IDS, doc.relatedReportIds)
            .append(FIELD_MESSAGE_COUNT, doc.messageCount)
            .append(FIELD_LAST_MESSAGE_AT, doc.lastMessageAt)
            .append(FIELD_RESOLVED_AT, doc.resolvedAt)
            .append(FIELD_CREATED_AT, doc.createdAt)
            .append(FIELD_UPDATED_AT, doc.updatedAt)
        mongoTemplate.insert(bson, COLLECTION_NAME)
        return doc.copy(id = id)
    }

    fun findById(id: ObjectId): TicketDocument? {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        return mongoTemplate.findOne(query, Document::class.java, COLLECTION_NAME)?.let { fromDocument(it) }
    }

    fun findAllPaginated(
        status: TicketStatus?,
        priority: TicketPriority?,
        category: TicketCategory?,
        assigneeId: ObjectId?,
        search: String?,
        sortField: String?,
        sortDirection: String?,
        pageable: Pageable
    ): Page<TicketDocument> {
        val criteria = mutableListOf<Criteria>()
        status?.let { criteria.add(Criteria.where(FIELD_STATUS).`is`(it.shortName)) }
        priority?.let { criteria.add(Criteria.where(FIELD_PRIORITY).`is`(it.shortName)) }
        category?.let { criteria.add(Criteria.where(FIELD_CATEGORY).`is`(it.shortName)) }
        assigneeId?.let { criteria.add(Criteria.where(FIELD_ASSIGNEE_ID).`is`(it)) }
        if (!search.isNullOrBlank()) {
            val regex = ".*${Regex.escape(search)}.*"
            criteria.add(Criteria.where(FIELD_SUBJECT).regex(regex, "i"))
        }

        val query = if (criteria.isEmpty()) Query() else Query(Criteria().andOperator(criteria))
        val total = mongoTemplate.count(query, COLLECTION_NAME)

        val sort = buildSort(sortField, sortDirection)
        query.with(sort).with(pageable)

        val docs = mongoTemplate.find(query, Document::class.java, COLLECTION_NAME).map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun findByCreatorIdPaginated(creatorId: ObjectId, pageable: Pageable): Page<TicketDocument> {
        val query = Query(Criteria.where(FIELD_CREATOR_ID).`is`(creatorId))
        val total = mongoTemplate.count(query, COLLECTION_NAME)
        query.with(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT)).with(pageable)

        val docs = mongoTemplate.find(query, Document::class.java, COLLECTION_NAME).map { fromDocument(it) }
        return PageImpl(docs, pageable, total)
    }

    fun update(
        id: ObjectId,
        status: TicketStatus?,
        priority: TicketPriority?,
        priorityOrder: Int?,
        assigneeId: ObjectId?,
        resolvedAt: Long?,
        updatedAt: Long
    ) {
        val update = Update().set(FIELD_UPDATED_AT, updatedAt)
        status?.let { update.set(FIELD_STATUS, it.shortName) }
        priority?.let { update.set(FIELD_PRIORITY, it.shortName) }
        priorityOrder?.let { update.set(FIELD_PRIORITY_ORDER, it) }
        assigneeId?.let { update.set(FIELD_ASSIGNEE_ID, it) }
        resolvedAt?.let { update.set(FIELD_RESOLVED_AT, it) }

        mongoTemplate.updateFirst(Query(Criteria.where(FIELD_ID).`is`(id)), update, COLLECTION_NAME)
    }

    fun incrementMessageCount(id: ObjectId, lastMessageAt: Long, updatedAt: Long) {
        val query = Query(Criteria.where(FIELD_ID).`is`(id))
        val update = Update()
            .inc(FIELD_MESSAGE_COUNT, 1)
            .set(FIELD_LAST_MESSAGE_AT, lastMessageAt)
            .set(FIELD_UPDATED_AT, updatedAt)
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME)
    }

    fun countByStatus(status: TicketStatus): Long {
        val query = Query(Criteria.where(FIELD_STATUS).`is`(status.shortName))
        return mongoTemplate.count(query, COLLECTION_NAME)
    }

    fun countResolvedSince(since: Long): Long {
        val query = Query(Criteria.where(FIELD_RESOLVED_AT).gte(since))
        return mongoTemplate.count(query, COLLECTION_NAME)
    }

    fun averageResolutionTimeMicros(): Long? {
        val matchStage = Aggregation.match(
            Criteria.where(FIELD_STATUS).`is`(TicketStatus.RESOLVED.shortName)
                .and(FIELD_RESOLVED_AT).ne(null)
        )
        val projectStage = Aggregation.project()
            .andExpression("\$$FIELD_RESOLVED_AT - \$$FIELD_CREATED_AT").`as`("resolutionTime")
        val groupStage = Aggregation.group().avg("resolutionTime").`as`("avgTime")

        val aggregation = Aggregation.newAggregation(matchStage, projectStage, groupStage)
        val result = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Document::class.java)
        val doc = result.uniqueMappedResult ?: return null
        return doc.get("avgTime")?.let { (it as Number).toLong() }
    }

    private fun buildSort(sortField: String?, sortDirection: String?): Sort {
        val direction = if (sortDirection == "ASC") Sort.Direction.ASC else Sort.Direction.DESC
        return when (sortField) {
            "createdAt" -> Sort.by(direction, FIELD_CREATED_AT)
            "updatedAt" -> Sort.by(direction, FIELD_UPDATED_AT)
            "priority" -> Sort.by(direction, FIELD_PRIORITY_ORDER).and(Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT))
            "lastMessageAt" -> Sort.by(direction, FIELD_LAST_MESSAGE_AT)
            else -> Sort.by(Sort.Direction.DESC, FIELD_CREATED_AT)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun fromDocument(doc: Document): TicketDocument = TicketDocument(
        id = doc.getObjectId(FIELD_ID),
        subject = doc.getString(FIELD_SUBJECT),
        category = doc.getString(FIELD_CATEGORY),
        status = doc.getString(FIELD_STATUS),
        priority = doc.getString(FIELD_PRIORITY),
        priorityOrder = doc.getInteger(FIELD_PRIORITY_ORDER),
        creatorId = doc.getObjectId(FIELD_CREATOR_ID),
        assigneeId = doc.getObjectId(FIELD_ASSIGNEE_ID),
        relatedReportIds = (doc.getList(FIELD_RELATED_REPORT_IDS, ObjectId::class.java) ?: emptyList()),
        messageCount = doc.getInteger(FIELD_MESSAGE_COUNT),
        lastMessageAt = doc.getLong(FIELD_LAST_MESSAGE_AT),
        resolvedAt = doc.getLong(FIELD_RESOLVED_AT),
        createdAt = doc.getLong(FIELD_CREATED_AT),
        updatedAt = doc.getLong(FIELD_UPDATED_AT)
    )
}

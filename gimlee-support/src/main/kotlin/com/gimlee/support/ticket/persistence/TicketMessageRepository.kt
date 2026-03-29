package com.gimlee.support.ticket.persistence

import com.gimlee.support.ticket.persistence.model.TicketMessageDocument
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument.Companion.FIELD_AUTHOR_ID
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument.Companion.FIELD_AUTHOR_ROLE
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument.Companion.FIELD_BODY
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument.Companion.FIELD_CREATED_AT
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument.Companion.FIELD_ID
import com.gimlee.support.ticket.persistence.model.TicketMessageDocument.Companion.FIELD_TICKET_ID
import org.bson.Document
import org.bson.types.ObjectId
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository

@Repository
class TicketMessageRepository(private val mongoTemplate: MongoTemplate) {

    companion object {
        const val COLLECTION_NAME = "gimlee-support-messages"
    }

    fun save(doc: TicketMessageDocument): TicketMessageDocument {
        val id = doc.id ?: ObjectId()
        val bson = Document()
            .append(FIELD_ID, id)
            .append(FIELD_TICKET_ID, doc.ticketId)
            .append(FIELD_AUTHOR_ID, doc.authorId)
            .append(FIELD_AUTHOR_ROLE, doc.authorRole)
            .append(FIELD_BODY, doc.body)
            .append(FIELD_CREATED_AT, doc.createdAt)
        mongoTemplate.insert(bson, COLLECTION_NAME)
        return doc.copy(id = id)
    }

    fun findByTicketId(ticketId: ObjectId): List<TicketMessageDocument> {
        val query = Query(Criteria.where(FIELD_TICKET_ID).`is`(ticketId))
            .with(Sort.by(Sort.Direction.ASC, FIELD_CREATED_AT))
        return mongoTemplate.find(query, Document::class.java, COLLECTION_NAME).map { fromDocument(it) }
    }

    private fun fromDocument(doc: Document): TicketMessageDocument = TicketMessageDocument(
        id = doc.getObjectId(FIELD_ID),
        ticketId = doc.getObjectId(FIELD_TICKET_ID),
        authorId = doc.getObjectId(FIELD_AUTHOR_ID),
        authorRole = doc.getString(FIELD_AUTHOR_ROLE),
        body = doc.getString(FIELD_BODY),
        createdAt = doc.getLong(FIELD_CREATED_AT)
    )
}

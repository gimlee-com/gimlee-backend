package com.gimlee.support.ticket.persistence.model

import com.gimlee.support.ticket.domain.model.TicketMessage
import com.gimlee.support.ticket.domain.model.TicketMessageRole
import org.bson.types.ObjectId

data class TicketMessageDocument(
    val id: ObjectId? = null,
    val ticketId: ObjectId,
    val authorId: ObjectId,
    val authorRole: String,
    val body: String,
    val createdAt: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_TICKET_ID = "tid"
        const val FIELD_AUTHOR_ID = "aid"
        const val FIELD_AUTHOR_ROLE = "ar"
        const val FIELD_BODY = "b"
        const val FIELD_CREATED_AT = "ca"
    }

    fun toDomain(): TicketMessage = TicketMessage(
        id = id!!.toHexString(),
        ticketId = ticketId.toHexString(),
        authorId = authorId.toHexString(),
        authorRole = TicketMessageRole.fromShortName(authorRole),
        body = body,
        createdAt = createdAt
    )
}

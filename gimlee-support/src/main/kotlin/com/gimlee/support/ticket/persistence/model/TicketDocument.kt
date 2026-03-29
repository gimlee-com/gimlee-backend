package com.gimlee.support.ticket.persistence.model

import com.gimlee.support.ticket.domain.model.Ticket
import com.gimlee.support.ticket.domain.model.TicketCategory
import com.gimlee.support.ticket.domain.model.TicketPriority
import com.gimlee.support.ticket.domain.model.TicketStatus
import org.bson.types.ObjectId

data class TicketDocument(
    val id: ObjectId? = null,
    val subject: String,
    val category: String,
    val status: String,
    val priority: String,
    val priorityOrder: Int,
    val creatorId: ObjectId,
    val assigneeId: ObjectId?,
    val relatedReportIds: List<ObjectId>,
    val messageCount: Int,
    val lastMessageAt: Long?,
    val resolvedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_SUBJECT = "sub"
        const val FIELD_CATEGORY = "cat"
        const val FIELD_STATUS = "st"
        const val FIELD_PRIORITY = "pri"
        const val FIELD_PRIORITY_ORDER = "po"
        const val FIELD_CREATOR_ID = "cid"
        const val FIELD_ASSIGNEE_ID = "aid"
        const val FIELD_RELATED_REPORT_IDS = "rids"
        const val FIELD_MESSAGE_COUNT = "mc"
        const val FIELD_LAST_MESSAGE_AT = "lma"
        const val FIELD_RESOLVED_AT = "ra"
        const val FIELD_CREATED_AT = "ca"
        const val FIELD_UPDATED_AT = "ua"
    }

    fun toDomain(): Ticket = Ticket(
        id = id!!.toHexString(),
        subject = subject,
        category = TicketCategory.fromShortName(category),
        status = TicketStatus.fromShortName(status),
        priority = TicketPriority.fromShortName(priority),
        creatorId = creatorId.toHexString(),
        assigneeId = assigneeId?.toHexString(),
        relatedReportIds = relatedReportIds.map { it.toHexString() },
        messageCount = messageCount,
        lastMessageAt = lastMessageAt,
        resolvedAt = resolvedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

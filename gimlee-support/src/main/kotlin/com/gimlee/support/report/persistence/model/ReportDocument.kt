package com.gimlee.support.report.persistence.model

import com.gimlee.support.report.domain.model.*
import org.bson.Document
import org.bson.types.ObjectId

data class ReportDocument(
    val id: ObjectId? = null,
    val targetId: ObjectId,
    val targetType: String,
    val contextId: ObjectId?,
    val reporterId: ObjectId,
    val reason: String,
    val description: String?,
    val status: String,
    val targetTitle: String?,
    val targetSnapshot: Document?,
    val assigneeId: ObjectId?,
    val resolution: String?,
    val resolvedBy: ObjectId?,
    val resolvedAt: Long?,
    val internalNotes: String?,
    val siblingCount: Long,
    val timeline: List<Document>,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_TARGET_ID = "tid"
        const val FIELD_TARGET_TYPE = "tt"
        const val FIELD_CONTEXT_ID = "cid"
        const val FIELD_REPORTER_ID = "rid"
        const val FIELD_REASON = "rsn"
        const val FIELD_DESCRIPTION = "desc"
        const val FIELD_STATUS = "st"
        const val FIELD_TARGET_TITLE = "ttl"
        const val FIELD_TARGET_SNAPSHOT = "snap"
        const val FIELD_ASSIGNEE_ID = "aid"
        const val FIELD_RESOLUTION = "res"
        const val FIELD_RESOLVED_BY = "rb"
        const val FIELD_RESOLVED_AT = "ra"
        const val FIELD_INTERNAL_NOTES = "in"
        const val FIELD_SIBLING_COUNT = "sc"
        const val FIELD_TIMELINE = "tl"
        const val FIELD_CREATED_AT = "ca"
        const val FIELD_UPDATED_AT = "ua"

        const val TL_FIELD_ID = "i"
        const val TL_FIELD_ACTION = "a"
        const val TL_FIELD_PERFORMED_BY = "pb"
        const val TL_FIELD_DETAIL = "dt"
        const val TL_FIELD_CREATED_AT = "ca"

        fun timelineEntryToDocument(entry: ReportTimelineEntry): Document = Document()
            .append(TL_FIELD_ID, entry.id)
            .append(TL_FIELD_ACTION, entry.action.shortName)
            .append(TL_FIELD_PERFORMED_BY, ObjectId(entry.performedBy))
            .append(TL_FIELD_DETAIL, entry.detail)
            .append(TL_FIELD_CREATED_AT, entry.createdAt)

        fun timelineEntryToDomain(doc: Document): ReportTimelineEntry = ReportTimelineEntry(
            id = doc.getString(TL_FIELD_ID),
            action = ReportTimelineAction.fromShortName(doc.getString(TL_FIELD_ACTION)),
            performedBy = (doc.get(TL_FIELD_PERFORMED_BY) as ObjectId).toHexString(),
            detail = doc.getString(TL_FIELD_DETAIL),
            createdAt = doc.getLong(TL_FIELD_CREATED_AT)
        )

        private fun docToMap(doc: Document): Map<String, Any?> = doc.toMutableMap()
    }

    fun toDomain(): Report = Report(
        id = id!!.toHexString(),
        targetId = targetId.toHexString(),
        targetType = ReportTargetType.fromShortName(targetType),
        contextId = contextId?.toHexString(),
        reporterId = reporterId.toHexString(),
        reason = ReportReason.fromShortName(reason),
        description = description,
        status = ReportStatus.fromShortName(status),
        targetTitle = targetTitle,
        targetSnapshot = targetSnapshot?.let { docToMap(it) },
        assigneeId = assigneeId?.toHexString(),
        resolution = resolution?.let { ReportResolution.fromShortName(it) },
        resolvedBy = resolvedBy?.toHexString(),
        resolvedAt = resolvedAt,
        internalNotes = internalNotes,
        siblingCount = siblingCount,
        timeline = timeline.map { timelineEntryToDomain(it) },
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

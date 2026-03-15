package com.gimlee.reports.persistence.model

import com.gimlee.common.InstantUtils
import com.gimlee.reports.domain.model.Report
import com.gimlee.reports.domain.model.ReportTargetType
import org.bson.types.ObjectId

data class ReportDocument(
    val id: ObjectId? = null,
    val targetId: ObjectId,
    val targetType: String,
    val contextId: ObjectId?,
    val reporterId: ObjectId,
    val reason: String,
    val createdAt: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_TARGET_ID = "tid"
        const val FIELD_TARGET_TYPE = "tt"
        const val FIELD_CONTEXT_ID = "cid"
        const val FIELD_REPORTER_ID = "rid"
        const val FIELD_REASON = "rsn"
        const val FIELD_CREATED_AT = "ca"
    }

    fun toDomain(): Report = Report(
        id = id!!.toHexString(),
        targetId = targetId.toHexString(),
        targetType = ReportTargetType.fromShortName(targetType),
        contextId = contextId?.toHexString(),
        reporterId = reporterId.toHexString(),
        reason = reason,
        createdAt = InstantUtils.fromMicros(createdAt)
    )
}

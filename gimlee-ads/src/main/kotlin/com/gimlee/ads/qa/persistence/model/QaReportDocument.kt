package com.gimlee.ads.qa.persistence.model

import com.gimlee.ads.qa.domain.model.QaReport
import com.gimlee.ads.qa.domain.model.QaReportTargetType
import com.gimlee.common.InstantUtils
import org.bson.types.ObjectId

data class QaReportDocument(
    val id: ObjectId? = null,
    val targetId: ObjectId,
    val targetType: String,
    val adId: ObjectId,
    val reporterId: ObjectId,
    val reason: String,
    val createdAt: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_TARGET_ID = "tid"
        const val FIELD_TARGET_TYPE = "tt"
        const val FIELD_AD_ID = "aid"
        const val FIELD_REPORTER_ID = "rid"
        const val FIELD_REASON = "rsn"
        const val FIELD_CREATED_AT = "ca"
    }

    fun toDomain(): QaReport = QaReport(
        id = id!!.toHexString(),
        targetId = targetId.toHexString(),
        targetType = QaReportTargetType.fromShortName(targetType),
        adId = adId.toHexString(),
        reporterId = reporterId.toHexString(),
        reason = reason,
        createdAt = InstantUtils.fromMicros(createdAt)
    )
}

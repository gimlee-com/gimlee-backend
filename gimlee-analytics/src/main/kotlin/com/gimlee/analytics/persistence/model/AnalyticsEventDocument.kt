package com.gimlee.analytics.persistence.model

import org.bson.types.ObjectId

data class AnalyticsEventDocument(
    val id: ObjectId,
    val type: String,
    val targetId: String?,
    val timestampMicros: Long,
    val sampleRate: Double,
    val userId: ObjectId?,
    val clientId: String?,
    val botScore: Double?,
    val userAgent: String?,
    val referrer: String?,
    val metadata: Map<String, String>?
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_TYPE = "t"
        const val FIELD_TARGET_ID = "tid"
        const val FIELD_TIMESTAMP = "ts"
        const val FIELD_SAMPLE_RATE = "sr"
        const val FIELD_USER_ID = "uid"
        const val FIELD_CLIENT_ID = "cid"
        const val FIELD_BOT_SCORE = "bs"
        const val FIELD_USER_AGENT = "ua"
        const val FIELD_REFERRER = "ref"
        const val FIELD_METADATA = "meta"
    }
}

package com.gimlee.ads.qa.persistence.model

import com.gimlee.ads.qa.domain.model.Question
import com.gimlee.ads.qa.domain.model.QuestionStatus
import com.gimlee.common.InstantUtils
import org.bson.types.ObjectId

data class QuestionDocument(
    val id: ObjectId? = null,
    val adId: ObjectId,
    val authorId: ObjectId,
    val text: String,
    val upvoteCount: Int,
    val isPinned: Boolean,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_AD_ID = "aid"
        const val FIELD_AUTHOR_ID = "uid"
        const val FIELD_TEXT = "t"
        const val FIELD_UPVOTE_COUNT = "uc"
        const val FIELD_IS_PINNED = "pin"
        const val FIELD_STATUS = "s"
        const val FIELD_CREATED_AT = "ca"
        const val FIELD_UPDATED_AT = "ua"
    }

    fun toDomain(): Question = Question(
        id = id!!.toHexString(),
        adId = adId.toHexString(),
        authorId = authorId.toHexString(),
        text = text,
        upvoteCount = upvoteCount,
        isPinned = isPinned,
        status = QuestionStatus.fromShortName(status),
        createdAt = InstantUtils.fromMicros(createdAt),
        updatedAt = InstantUtils.fromMicros(updatedAt)
    )
}

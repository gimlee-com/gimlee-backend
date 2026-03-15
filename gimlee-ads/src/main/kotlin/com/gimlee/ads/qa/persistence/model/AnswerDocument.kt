package com.gimlee.ads.qa.persistence.model

import com.gimlee.ads.qa.domain.model.Answer
import com.gimlee.ads.qa.domain.model.AnswerType
import com.gimlee.common.InstantUtils
import org.bson.types.ObjectId

data class AnswerDocument(
    val id: ObjectId? = null,
    val questionId: ObjectId,
    val authorId: ObjectId,
    val type: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val FIELD_ID = "_id"
        const val FIELD_QUESTION_ID = "qid"
        const val FIELD_AUTHOR_ID = "uid"
        const val FIELD_TYPE = "tp"
        const val FIELD_TEXT = "t"
        const val FIELD_CREATED_AT = "ca"
        const val FIELD_UPDATED_AT = "ua"
    }

    fun toDomain(): Answer = Answer(
        id = id!!.toHexString(),
        questionId = questionId.toHexString(),
        authorId = authorId.toHexString(),
        type = AnswerType.fromShortName(type),
        text = text,
        createdAt = InstantUtils.fromMicros(createdAt),
        updatedAt = InstantUtils.fromMicros(updatedAt)
    )
}

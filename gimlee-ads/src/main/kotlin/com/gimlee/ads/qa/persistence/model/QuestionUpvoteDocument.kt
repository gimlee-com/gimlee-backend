package com.gimlee.ads.qa.persistence.model

import org.bson.types.ObjectId

data class QuestionUpvoteDocument(
    val questionId: ObjectId,
    val userId: ObjectId,
    val createdAt: Long
) {
    companion object {
        const val FIELD_QUESTION_ID = "qid"
        const val FIELD_USER_ID = "uid"
        const val FIELD_CREATED_AT = "ca"
    }
}

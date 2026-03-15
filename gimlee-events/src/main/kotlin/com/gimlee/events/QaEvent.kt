package com.gimlee.events

import java.time.Instant

data class QuestionAskedEvent(
    val questionId: String,
    val adId: String,
    val authorId: String,
    val sellerId: String,
    val timestamp: Instant = Instant.now()
)

data class QuestionAnsweredEvent(
    val questionId: String,
    val answerId: String,
    val adId: String,
    val questionAuthorId: String,
    val answerAuthorId: String,
    val answerType: String,
    val timestamp: Instant = Instant.now()
)

data class QuestionUpvoteMilestoneEvent(
    val questionId: String,
    val adId: String,
    val sellerId: String,
    val upvoteCount: Int,
    val timestamp: Instant = Instant.now()
)

data class QaContentReportedEvent(
    val targetId: String,
    val targetType: String,
    val adId: String,
    val reporterId: String,
    val reason: String,
    val timestamp: Instant = Instant.now()
)

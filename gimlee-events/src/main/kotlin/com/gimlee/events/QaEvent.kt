package com.gimlee.events

import java.time.Instant

data class QuestionAskedEvent(
    val questionId: String,
    val adId: String,
    val adTitle: String,
    val authorId: String,
    val sellerId: String,
    val timestamp: Instant = Instant.now()
)

data class QuestionAnsweredEvent(
    val questionId: String,
    val answerId: String,
    val adId: String,
    val adTitle: String,
    val questionAuthorId: String,
    val answerAuthorId: String,
    val answerType: String,
    val timestamp: Instant = Instant.now()
)

data class QuestionUpvoteMilestoneEvent(
    val questionId: String,
    val adId: String,
    val adTitle: String,
    val sellerId: String,
    val upvoteCount: Int,
    val timestamp: Instant = Instant.now()
)



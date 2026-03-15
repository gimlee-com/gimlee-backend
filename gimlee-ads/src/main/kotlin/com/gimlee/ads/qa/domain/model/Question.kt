package com.gimlee.ads.qa.domain.model

import java.time.Instant

data class Question(
    val id: String,
    val adId: String,
    val authorId: String,
    val text: String,
    val upvoteCount: Int,
    val isPinned: Boolean,
    val status: QuestionStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)

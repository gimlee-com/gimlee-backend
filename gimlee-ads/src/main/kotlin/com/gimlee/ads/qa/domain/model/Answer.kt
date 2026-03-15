package com.gimlee.ads.qa.domain.model

import java.time.Instant

data class Answer(
    val id: String,
    val questionId: String,
    val authorId: String,
    val type: AnswerType,
    val text: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

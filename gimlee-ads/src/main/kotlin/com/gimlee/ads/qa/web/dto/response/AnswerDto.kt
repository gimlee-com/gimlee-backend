package com.gimlee.ads.qa.web.dto.response

import com.gimlee.ads.qa.domain.model.AnswerType
import java.time.Instant

data class AnswerDto(
    val id: String,
    val questionId: String,
    val authorId: String,
    val authorUsername: String? = null,
    val type: AnswerType,
    val text: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

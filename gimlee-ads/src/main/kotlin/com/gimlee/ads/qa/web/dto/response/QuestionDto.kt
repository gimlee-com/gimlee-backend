package com.gimlee.ads.qa.web.dto.response

import com.gimlee.ads.qa.domain.model.QuestionStatus
import java.time.Instant

data class QuestionDto(
    val id: String,
    val adId: String,
    val authorId: String,
    val authorUsername: String? = null,
    val authorAvatarUrl: String? = null,
    val text: String,
    val upvoteCount: Int,
    val isPinned: Boolean,
    val isUpvotedByMe: Boolean = false,
    val status: QuestionStatus,
    val answerCount: Int = 0,
    val answers: List<AnswerDto> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)

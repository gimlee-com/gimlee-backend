package com.gimlee.ratings.domain.model

data class Rating(
    val id: String,
    val contextType: String,
    val contextId: String,
    val subjectKind: SubjectKind,
    val repKind: RepKind,
    val raterId: String,
    val rateeId: String,
    val score: Int,
    val title: String? = null,
    val body: String? = null,
    val photoPaths: List<String>? = null,
    val snapshot: RatingSubjectSnapshot? = null,
    val status: RatingStatus = RatingStatus.PUBLISHED,
    val edited: Boolean = false,
    val editableUntil: Long,
    val supplements: List<RatingSupplement>? = null,
    val response: RatingResponse? = null,
    val helpfulCount: Int = 0,
    val reportCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val publishedAt: Long? = null
)

package com.gimlee.ratings.domain.model

data class RatingEligibility(
    val id: String,
    val contextType: String,
    val contextId: String,
    val raterId: String,
    val rateeId: String,
    val repKind: RepKind,
    val snapshot: RatingSubjectSnapshot,
    val status: EligibilityStatus = EligibilityStatus.PENDING,
    val ratingId: String? = null,
    val activeFrom: Long,
    val expiresAt: Long,
    val createdAt: Long
)

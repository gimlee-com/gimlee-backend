package com.gimlee.ratings.domain.model

data class RatingSupplement(
    val id: String,
    val body: String,
    val status: RatingStatus,
    val editableUntil: Long,
    val createdAt: Long
)

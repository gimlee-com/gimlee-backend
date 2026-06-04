package com.gimlee.ratings.domain.model

data class RatingSubjectSnapshot(
    val refType: String,
    val items: List<RatingSnapshotItem>
)

data class RatingSnapshotItem(
    val adId: String,
    val name: String,
    val quantity: Int,
    val unitPrice: String,
    val currency: String,
    val thumbPath: String? = null
)

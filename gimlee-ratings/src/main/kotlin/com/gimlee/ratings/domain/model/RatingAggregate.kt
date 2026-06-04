package com.gimlee.ratings.domain.model

data class RatingAggregate(
    val rateeId: String,
    val repKind: RepKind,
    val subjectKind: SubjectKind = SubjectKind.USER,
    val count: Int = 0,
    val sum: Long = 0,
    val dist: Map<String, Int> = mapOf("1" to 0, "2" to 0, "3" to 0, "4" to 0, "5" to 0),
    val lastRatingAt: Long? = null,
    val updatedAt: Long
) {
    val average: Double
        get() = if (count > 0) sum.toDouble() / count else 0.0
}

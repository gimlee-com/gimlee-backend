package com.gimlee.ratings.persistence.model

import com.gimlee.ratings.domain.model.RatingAggregate
import com.gimlee.ratings.domain.model.RepKind
import com.gimlee.ratings.domain.model.SubjectKind

data class RatingAggregateDocument(
    val rateeId: String,
    val repKind: String,
    val subjectKind: String,
    val count: Int,
    val sum: Long,
    val dist: Map<String, Int>,
    val lastRatingAt: Long?,
    val updatedAt: Long
) {
    companion object {
        const val COLLECTION_NAME = "gimlee-ratings-aggregates"

        const val FIELD_ID = "_id"
        const val FIELD_RATEE_ID = "rte"
        const val FIELD_REP_KIND = "rk"
        const val FIELD_SUBJECT_KIND = "sk"
        const val FIELD_COUNT = "n"
        const val FIELD_SUM = "sm"
        const val FIELD_DIST = "ds"
        const val FIELD_LAST_RATING_AT = "lr"
        const val FIELD_UPDATED_AT = "ua"

        fun compositeId(rateeId: String, repKind: String): String = "${rateeId}_${repKind}"

        fun fromDomain(domain: RatingAggregate): RatingAggregateDocument = RatingAggregateDocument(
            rateeId = domain.rateeId,
            repKind = domain.repKind.shortName,
            subjectKind = domain.subjectKind.shortName,
            count = domain.count,
            sum = domain.sum,
            dist = domain.dist,
            lastRatingAt = domain.lastRatingAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(): RatingAggregate = RatingAggregate(
        rateeId = rateeId,
        repKind = RepKind.fromShortName(repKind),
        subjectKind = SubjectKind.fromShortName(subjectKind),
        count = count,
        sum = sum,
        dist = dist,
        lastRatingAt = lastRatingAt,
        updatedAt = updatedAt
    )
}

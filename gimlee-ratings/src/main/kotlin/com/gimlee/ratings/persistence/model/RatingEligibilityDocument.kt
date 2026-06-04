package com.gimlee.ratings.persistence.model

import com.gimlee.ratings.domain.model.*

data class RatingEligibilityDocument(
    val id: String,
    val contextType: String,
    val contextId: String,
    val raterId: String,
    val rateeId: String,
    val repKind: String,
    val snapshot: RatingSnapshotDocument,
    val status: String,
    val ratingId: String? = null,
    val activeFrom: Long,
    val expiresAt: Long,
    val createdAt: Long
) {
    companion object {
        const val COLLECTION_NAME = "gimlee-ratings-eligibility"

        const val FIELD_ID = "_id"
        const val FIELD_CONTEXT_TYPE = "ct"
        const val FIELD_CONTEXT_ID = "cid"
        const val FIELD_RATER_ID = "rtr"
        const val FIELD_RATEE_ID = "rte"
        const val FIELD_REP_KIND = "rk"
        const val FIELD_SNAPSHOT = "snp"
        const val FIELD_STATUS = "st"
        const val FIELD_RATING_ID = "rid"
        const val FIELD_ACTIVE_FROM = "af"
        const val FIELD_EXPIRES_AT = "exp"
        const val FIELD_CREATED_AT = "ca"

        fun fromDomain(domain: RatingEligibility): RatingEligibilityDocument = RatingEligibilityDocument(
            id = domain.id,
            contextType = domain.contextType,
            contextId = domain.contextId,
            raterId = domain.raterId,
            rateeId = domain.rateeId,
            repKind = domain.repKind.shortName,
            snapshot = RatingSnapshotDocument.fromDomain(domain.snapshot),
            status = domain.status.shortName,
            ratingId = domain.ratingId,
            activeFrom = domain.activeFrom,
            expiresAt = domain.expiresAt,
            createdAt = domain.createdAt
        )
    }

    fun toDomain(): RatingEligibility = RatingEligibility(
        id = id,
        contextType = contextType,
        contextId = contextId,
        raterId = raterId,
        rateeId = rateeId,
        repKind = RepKind.fromShortName(repKind),
        snapshot = snapshot.toDomain(),
        status = EligibilityStatus.fromShortName(status),
        ratingId = ratingId,
        activeFrom = activeFrom,
        expiresAt = expiresAt,
        createdAt = createdAt
    )
}

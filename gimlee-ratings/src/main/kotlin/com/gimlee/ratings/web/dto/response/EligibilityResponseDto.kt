package com.gimlee.ratings.web.dto.response

import com.gimlee.ratings.domain.model.RatingEligibility
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A rating eligibility record")
data class EligibilityResponseDto(
    @Schema(description = "Eligibility ID")
    val id: String,

    @Schema(description = "Context type (e.g., ORDER)")
    val contextType: String,

    @Schema(description = "Context ID (e.g., purchase ID)")
    val contextId: String,

    @Schema(description = "Who may rate (user ID)")
    val raterId: String,

    @Schema(description = "Who will be rated (user ID)")
    val rateeId: String,

    @Schema(description = "Reputation kind (SEL = seller, BUY = buyer)")
    val repKind: String,

    @Schema(description = "Eligibility status (PND = pending, CSD = consumed)")
    val status: String,

    @Schema(description = "Earliest submission time (epoch micros, dwell end). Before this time, POST /ratings is rejected with RATING_DWELL_NOT_ELAPSED.")
    val activeFrom: Long,

    @Schema(description = "Eligibility expiry time (epoch micros). After this time, the eligibility is hard-deleted.")
    val expiresAt: Long,

    @Schema(description = "Immutable item snapshot at transaction time — provides context for what is being rated")
    val snapshot: RatingSnapshotDto?,

    @Schema(description = "Creation timestamp (epoch micros)")
    val createdAt: Long
) {
    companion object {
        fun fromDomain(eligibility: RatingEligibility): EligibilityResponseDto = EligibilityResponseDto(
            id = eligibility.id,
            contextType = eligibility.contextType,
            contextId = eligibility.contextId,
            raterId = eligibility.raterId,
            rateeId = eligibility.rateeId,
            repKind = eligibility.repKind.shortName,
            status = eligibility.status.shortName,
            activeFrom = eligibility.activeFrom,
            expiresAt = eligibility.expiresAt,
            snapshot = eligibility.snapshot?.let { RatingSnapshotDto.fromDomain(it) },
            createdAt = eligibility.createdAt
        )
    }
}

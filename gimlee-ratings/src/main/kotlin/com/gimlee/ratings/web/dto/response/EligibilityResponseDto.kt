package com.gimlee.ratings.web.dto.response

import com.gimlee.ratings.domain.model.RatingEligibility
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A rating eligibility record")
data class EligibilityResponseDto(
    @Schema(description = "Eligibility ID")
    val id: String,

    @Schema(description = "Context type (e.g., ORDER)")
    val contextType: String,

    @Schema(description = "Context ID")
    val contextId: String,

    @Schema(description = "Who may rate")
    val raterId: String,

    @Schema(description = "Who will be rated")
    val rateeId: String,

    @Schema(description = "Reputation kind (SEL / BUY)")
    val repKind: String,

    @Schema(description = "Eligibility status (PND / CSD)")
    val status: String,

    @Schema(description = "Earliest submission time (epoch micros, dwell end)")
    val activeFrom: Long,

    @Schema(description = "Eligibility expiry time (epoch micros)")
    val expiresAt: Long,

    @Schema(description = "Item snapshot for context display")
    val snapshot: SnapshotResponseDto?,

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
            snapshot = SnapshotResponseDto.fromDomain(eligibility.snapshot),
            createdAt = eligibility.createdAt
        )
    }
}

@Schema(description = "Immutable snapshot of transacted items at the time of the transaction")
data class SnapshotResponseDto(
    @Schema(description = "Snapshot reference type")
    val refType: String,

    @Schema(description = "Snapshotted items")
    val items: List<SnapshotItemResponseDto>
) {
    companion object {
        fun fromDomain(snapshot: com.gimlee.ratings.domain.model.RatingSubjectSnapshot): SnapshotResponseDto =
            SnapshotResponseDto(
                refType = snapshot.refType,
                items = snapshot.items.map { SnapshotItemResponseDto.fromDomain(it) }
            )
    }
}

@Schema(description = "A snapshotted item from a transaction")
data class SnapshotItemResponseDto(
    @Schema(description = "Ad ID")
    val adId: String,

    @Schema(description = "Item name at transaction time")
    val name: String,

    @Schema(description = "Quantity purchased")
    val quantity: Int,

    @Schema(description = "Thumbnail media path")
    val thumbPath: String?
) {
    companion object {
        fun fromDomain(item: com.gimlee.ratings.domain.model.RatingSnapshotItem): SnapshotItemResponseDto =
            SnapshotItemResponseDto(
                adId = item.adId,
                name = item.name,
                quantity = item.quantity,
                thumbPath = item.thumbPath
            )
    }
}

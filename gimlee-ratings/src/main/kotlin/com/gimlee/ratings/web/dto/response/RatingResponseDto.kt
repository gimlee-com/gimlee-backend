package com.gimlee.ratings.web.dto.response

import com.gimlee.ratings.domain.model.Rating
import com.gimlee.ratings.domain.model.RatingSubjectSnapshot
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A user rating/review")
data class RatingResponseDto(
    @Schema(description = "Rating ID")
    val id: String,

    @Schema(description = "Context type (e.g., ORDER)")
    val contextType: String,

    @Schema(description = "Context ID (e.g., purchase ID)")
    val contextId: String,

    @Schema(description = "Reputation kind (SEL = seller, BUY = buyer)")
    val repKind: String,

    @Schema(description = "Author user ID")
    val raterId: String,

    @Schema(description = "Rated user ID")
    val rateeId: String,

    @Schema(description = "Rating score (1-5)")
    val score: Int,

    @Schema(description = "Short headline")
    val title: String?,

    @Schema(description = "Review body (markdown)")
    val body: String?,

    @Schema(description = "Review photo paths")
    val photoPaths: List<String>?,

    @Schema(description = "Immutable snapshot of transacted items at transaction time")
    val snapshot: RatingSnapshotDto?,

    @Schema(description = "Rating status (PUB = published, HID = hidden by moderation, DEL = deleted by author)")
    val status: String,

    @Schema(description = "Whether the rating has been edited")
    val edited: Boolean,

    @Schema(
        description = "Epoch micros timestamp until which the rating is freely editable. " +
            "Once the current time passes this value, the rating is frozen and only supplements are allowed (after cooldown)."
    )
    val editableUntil: Long,

    @Schema(description = "Supplements (append-only follow-ups)")
    val supplements: List<SupplementResponseDto>?,

    @Schema(description = "Ratee response")
    val response: RatingResponseResponseDto?,

    @Schema(description = "Helpful vote count")
    val helpfulCount: Int,

    @Schema(description = "Creation timestamp (epoch micros)")
    val createdAt: Long,

    @Schema(description = "Last update timestamp (epoch micros)")
    val updatedAt: Long,

    @Schema(description = "Publication timestamp (epoch micros), null when hidden or deleted")
    val publishedAt: Long?
) {
    companion object {
        fun fromDomain(rating: Rating): RatingResponseDto = RatingResponseDto(
            id = rating.id,
            contextType = rating.contextType,
            contextId = rating.contextId,
            repKind = rating.repKind.shortName,
            raterId = rating.raterId,
            rateeId = rating.rateeId,
            score = rating.score,
            title = rating.title,
            body = rating.body,
            photoPaths = rating.photoPaths,
            snapshot = rating.snapshot?.let { RatingSnapshotDto.fromDomain(it) },
            status = rating.status.shortName,
            edited = rating.edited,
            editableUntil = rating.editableUntil,
            supplements = rating.supplements?.map { SupplementResponseDto.fromDomain(it) },
            response = rating.response?.let { RatingResponseResponseDto.fromDomain(it) },
            helpfulCount = rating.helpfulCount,
            createdAt = rating.createdAt,
            updatedAt = rating.updatedAt,
            publishedAt = rating.publishedAt
        )
    }
}

@Schema(description = "Immutable snapshot of transacted items at the time of the transaction")
data class RatingSnapshotDto(
    @Schema(description = "Snapshot reference type (e.g., ORDER_ITEMS)")
    val refType: String,

    @Schema(description = "Snapshotted items")
    val items: List<RatingSnapshotItemDto>
) {
    companion object {
        fun fromDomain(snapshot: RatingSubjectSnapshot): RatingSnapshotDto =
            RatingSnapshotDto(
                refType = snapshot.refType,
                items = snapshot.items.map { RatingSnapshotItemDto.fromDomain(it) }
            )
    }
}

@Schema(description = "A snapshotted item from a transaction")
data class RatingSnapshotItemDto(
    @Schema(description = "Original ad ID (may now be deleted or edited)")
    val adId: String,

    @Schema(description = "Ad title at transaction time — the only snapshot field shown in public projections")
    val name: String,

    @Schema(description = "Quantity purchased")
    val quantity: Int,

    @Schema(description = "Unit price at transaction time")
    val unitPrice: String,

    @Schema(description = "Settlement currency code")
    val currency: String,

    @Schema(description = "Thumbnail media path of the item's main photo at snapshot time")
    val thumbPath: String?
) {
    companion object {
        fun fromDomain(item: com.gimlee.ratings.domain.model.RatingSnapshotItem): RatingSnapshotItemDto =
            RatingSnapshotItemDto(
                adId = item.adId,
                name = item.name,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                currency = item.currency,
                thumbPath = item.thumbPath
            )
    }
}

@Schema(description = "A supplement (follow-up note) on a rating")
data class SupplementResponseDto(
    @Schema(description = "Supplement ID")
    val id: String,

    @Schema(description = "Supplement body (markdown)")
    val body: String,

    @Schema(description = "Supplement status (PUB = published, HID = hidden by moderation, DEL = deleted)")
    val status: String,

    @Schema(
        description = "Epoch micros timestamp until which this supplement is freely editable. " +
            "Once the current time passes this value, the supplement is frozen."
    )
    val editableUntil: Long,

    @Schema(description = "Creation timestamp (epoch micros)")
    val createdAt: Long
) {
    companion object {
        fun fromDomain(supplement: com.gimlee.ratings.domain.model.RatingSupplement): SupplementResponseDto =
            SupplementResponseDto(
                id = supplement.id,
                body = supplement.body,
                status = supplement.status.shortName,
                editableUntil = supplement.editableUntil,
                createdAt = supplement.createdAt
            )
    }
}

@Schema(description = "A ratee's response to a rating")
data class RatingResponseResponseDto(
    @Schema(description = "Response body (markdown)")
    val body: String,

    @Schema(description = "Creation timestamp (epoch micros)")
    val createdAt: Long,

    @Schema(description = "Last update timestamp (epoch micros)")
    val updatedAt: Long
) {
    companion object {
        fun fromDomain(response: com.gimlee.ratings.domain.model.RatingResponse): RatingResponseResponseDto =
            RatingResponseResponseDto(
                body = response.body,
                createdAt = response.createdAt,
                updatedAt = response.updatedAt
            )
    }
}

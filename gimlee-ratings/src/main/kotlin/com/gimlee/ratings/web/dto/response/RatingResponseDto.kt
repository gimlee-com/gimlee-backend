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

    @Schema(description = "Context ID")
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

    @Schema(description = "Whether the rating has been edited")
    val edited: Boolean,

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

    @Schema(description = "Publication timestamp (epoch micros)")
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
            edited = rating.edited,
            supplements = rating.supplements?.map { SupplementResponseDto.fromDomain(it) },
            response = rating.response?.let { RatingResponseResponseDto.fromDomain(it) },
            helpfulCount = rating.helpfulCount,
            createdAt = rating.createdAt,
            updatedAt = rating.updatedAt,
            publishedAt = rating.publishedAt
        )
    }
}

@Schema(description = "A supplement (follow-up note) on a rating")
data class SupplementResponseDto(
    @Schema(description = "Supplement ID")
    val id: String,

    @Schema(description = "Supplement body (markdown)")
    val body: String,

    @Schema(description = "Creation timestamp (epoch micros)")
    val createdAt: Long
) {
    companion object {
        fun fromDomain(supplement: com.gimlee.ratings.domain.model.RatingSupplement): SupplementResponseDto =
            SupplementResponseDto(
                id = supplement.id,
                body = supplement.body,
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

package com.gimlee.ratings.web.dto.response

import com.gimlee.ratings.domain.model.RatingAggregate
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Aggregated reputation statistics for a user")
data class RatingAggregateResponseDto(
    @Schema(description = "Rated user ID")
    val rateeId: String,

    @Schema(description = "Reputation kind (SEL / BUY)")
    val repKind: String,

    @Schema(description = "Number of visible ratings")
    val count: Int,

    @Schema(description = "Average score")
    val average: Double,

    @Schema(description = "Star distribution histogram")
    val dist: Map<String, Int>,

    @Schema(description = "Last rating timestamp (epoch micros)")
    val lastRatingAt: Long?
) {
    companion object {
        fun fromDomain(aggregate: RatingAggregate): RatingAggregateResponseDto = RatingAggregateResponseDto(
            rateeId = aggregate.rateeId,
            repKind = aggregate.repKind.shortName,
            count = aggregate.count,
            average = aggregate.average,
            dist = aggregate.dist,
            lastRatingAt = aggregate.lastRatingAt
        )
    }
}

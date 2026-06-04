package com.gimlee.ratings.domain

import com.gimlee.common.toMicros
import com.gimlee.ratings.domain.model.RatingAggregate
import com.gimlee.ratings.persistence.RatingAggregateRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class RatingAggregateService(
    private val aggregateRepository: RatingAggregateRepository
) {

    fun findByRateeAndRepKind(rateeId: String, repKind: String): RatingAggregate? =
        aggregateRepository.findByRateeAndRepKind(rateeId, repKind)?.toDomain()

    fun onRatingPublished(
        rateeId: String,
        repKind: String,
        subjectKind: String,
        score: Int,
        ratingAt: Long
    ) {
        aggregateRepository.upsertOnPublish(
            rateeId = rateeId,
            repKind = repKind,
            subjectKind = subjectKind,
            score = score,
            ratingAt = ratingAt,
            updatedAt = Instant.now().toMicros()
        )
    }

    fun onRatingHidden(rateeId: String, repKind: String, score: Int) {
        aggregateRepository.updateOnHide(
            rateeId = rateeId,
            repKind = repKind,
            score = score,
            updatedAt = Instant.now().toMicros()
        )
    }

    fun onRatingRestored(rateeId: String, repKind: String, score: Int, ratingAt: Long) {
        aggregateRepository.updateOnRestore(
            rateeId = rateeId,
            repKind = repKind,
            score = score,
            ratingAt = ratingAt,
            updatedAt = Instant.now().toMicros()
        )
    }
}

package com.gimlee.ratings.domain

import com.gimlee.common.UUIDv7
import com.gimlee.common.toMicros
import com.gimlee.ratings.domain.model.*
import com.gimlee.ratings.persistence.RatingEligibilityRepository
import com.gimlee.ratings.persistence.model.RatingEligibilityDocument
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class RatingEligibilityService(
    private val eligibilityRepository: RatingEligibilityRepository,
    private val strategies: List<RatingContextStrategy>,
    @Value("\${gimlee.ratings.sweeper.interval-ms:300000}") private val sweeperIntervalMs: Long,
    @Value("\${gimlee.ratings.sweeper.batch-size:500}") private val sweeperBatchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun grant(
        contextType: String,
        contextId: String,
        raterId: String,
        rateeId: String,
        repKind: RepKind,
        snapshot: RatingSubjectSnapshot,
        completedAt: Instant,
        dwellTime: Duration,
        ratingWindow: Duration
    ): RatingEligibility? {
        val now = Instant.now()
        val activeFrom = completedAt.plus(dwellTime).toMicros()
        val expiresAt = completedAt.plus(dwellTime).plus(ratingWindow).toMicros()

        val eligibility = RatingEligibility(
            id = UUIDv7.generate().toString(),
            contextType = contextType,
            contextId = contextId,
            raterId = raterId,
            rateeId = rateeId,
            repKind = repKind,
            snapshot = snapshot,
            status = EligibilityStatus.PENDING,
            activeFrom = activeFrom,
            expiresAt = expiresAt,
            createdAt = now.toMicros()
        )

        val doc = RatingEligibilityDocument.fromDomain(eligibility)
        val saved = eligibilityRepository.save(doc) ?: return null
        return saved.toDomain()
    }

    fun findPendingByRater(raterId: String, pageable: Pageable): Page<RatingEligibility> =
        eligibilityRepository.findPendingByRaterPaginated(raterId, pageable)
            .map { it.toDomain() }

    fun findPendingEligibility(contextId: String, raterId: String, contextType: String): RatingEligibility? =
        eligibilityRepository.findPendingByContextAndRater(contextId, raterId, contextType)
            ?.toDomain()

    fun consume(id: String, ratingId: String): Boolean =
        eligibilityRepository.consumeEligibility(id, ratingId)

    @Scheduled(fixedDelayString = "\${gimlee.ratings.sweeper.interval-ms:300000}")
    fun sweepExpired() {
        val now = Instant.now().toMicros()
        log.info("Starting eligibility expiry sweep, batchSize={}", sweeperBatchSize)
        val expired = eligibilityRepository.findExpiredPending(now, sweeperBatchSize)
        if (expired.isEmpty()) {
            log.info("Eligibility expiry sweep complete, processed=0")
            return
        }
        val ids = expired.map { it.id }
        eligibilityRepository.deleteByIds(ids)
        log.info("Eligibility expiry sweep complete, processed={}", ids.size)
    }

    fun getStrategy(contextType: String): RatingContextStrategy =
        strategies.firstOrNull { it.supports(contextType) }
            ?: throw IllegalStateException("No RatingContextStrategy found for contextType: $contextType")
}

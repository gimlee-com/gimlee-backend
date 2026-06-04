package com.gimlee.ratings.domain

import com.gimlee.common.UUIDv7
import com.gimlee.common.toMicros
import com.gimlee.ratings.domain.model.*
import com.gimlee.ratings.persistence.RatingRepository
import com.gimlee.ratings.persistence.model.RatingDocument
import com.gimlee.ratings.persistence.model.RatingResponseDocument
import com.gimlee.ratings.persistence.model.RatingSupplementDocument
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class RatingService(
    private val ratingRepository: RatingRepository,
    private val eligibilityService: RatingEligibilityService,
    private val aggregateService: RatingAggregateService,
    private val contentValidator: MarkdownContentValidator
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createRating(
        contextType: String,
        contextId: String,
        raterId: String,
        score: Int,
        title: String?,
        body: String?,
        photoPaths: List<String>?
    ): Pair<RatingOutcome, Rating?> {
        if (score !in 1..5) return RatingOutcome.RATING_INVALID_SCORE to null

        val bodyValidation = contentValidator.validateBody(body)
        if (!bodyValidation.isValid) return RatingOutcome.RATING_BODY_NOT_SANITIZED to null
        val titleValidation = contentValidator.validateTitle(title)
        if (!titleValidation.isValid) return RatingOutcome.RATING_BODY_NOT_SANITIZED to null

        val eligibility = eligibilityService.findPendingEligibility(contextId, raterId, contextType)
            ?: return RatingOutcome.ELIGIBILITY_NOT_FOUND to null

        val now = Instant.now()
        val nowMicros = now.toMicros()

        if (nowMicros < eligibility.activeFrom) {
            return RatingOutcome.RATING_DWELL_NOT_ELAPSED to null
        }

        val existing = ratingRepository.findByContextAndRater(contextId, raterId, contextType)
        if (existing != null) return RatingOutcome.RATING_ALREADY_EXISTS to null

        val strategy = eligibilityService.getStrategy(contextType)
        val editableUntil = now.plus(strategy.editWindow()).toMicros()

        val rating = Rating(
            id = UUIDv7.generate().toString(),
            contextType = eligibility.contextType,
            contextId = eligibility.contextId,
            subjectKind = SubjectKind.USER,
            repKind = eligibility.repKind,
            raterId = eligibility.raterId,
            rateeId = eligibility.rateeId,
            score = score,
            title = title,
            body = body,
            photoPaths = photoPaths,
            snapshot = eligibility.snapshot,
            status = RatingStatus.PUBLISHED,
            edited = false,
            editableUntil = editableUntil,
            createdAt = nowMicros,
            updatedAt = nowMicros,
            publishedAt = nowMicros
        )

        val doc = RatingDocument.fromDomain(rating)
        ratingRepository.save(doc)

        eligibilityService.consume(eligibility.id, rating.id)

        aggregateService.onRatingPublished(
            rateeId = rating.rateeId,
            repKind = rating.repKind.shortName,
            subjectKind = rating.subjectKind.shortName,
            score = rating.score,
            ratingAt = nowMicros
        )

        log.info("Rating created: id={}, context={}/{}, rater={}, ratee={}, score={}",
            rating.id, contextType, contextId, raterId, rating.rateeId, score)

        return RatingOutcome.RATING_CREATED to rating
    }

    fun editRating(
        ratingId: String,
        raterId: String,
        score: Int,
        title: String?,
        body: String?,
        photoPaths: List<String>?
    ): Pair<RatingOutcome, Rating?> {
        if (score !in 1..5) return RatingOutcome.RATING_INVALID_SCORE to null

        val bodyValidation = contentValidator.validateBody(body)
        if (!bodyValidation.isValid) return RatingOutcome.RATING_BODY_NOT_SANITIZED to null
        val titleValidation = contentValidator.validateTitle(title)
        if (!titleValidation.isValid) return RatingOutcome.RATING_BODY_NOT_SANITIZED to null

        val existing = ratingRepository.findById(ratingId) ?: return RatingOutcome.RATING_NOT_FOUND to null
        if (existing.raterId != raterId) return RatingOutcome.RATING_NOT_AUTHORIZED to null

        val nowMicros = Instant.now().toMicros()
        if (nowMicros >= existing.editableUntil) {
            return RatingOutcome.RATING_EDIT_WINDOW_CLOSED to null
        }

        val strategy = eligibilityService.getStrategy(existing.contextType)
        val newEditableUntil = Instant.now().plus(strategy.editWindow()).toMicros()
        val edited = true

        ratingRepository.updateRating(
            id = ratingId,
            score = score,
            title = title,
            body = body,
            photoPaths = photoPaths,
            edited = edited,
            editableUntil = newEditableUntil,
            updatedAt = nowMicros
        )

        val updated = ratingRepository.findById(ratingId)!!
        return RatingOutcome.RATING_UPDATED to updated.toDomain()
    }

    fun addSupplement(
        ratingId: String,
        raterId: String,
        body: String
    ): Pair<RatingOutcome, Rating?> {
        val bodyValidation = contentValidator.validateBody(body)
        if (!bodyValidation.isValid) return RatingOutcome.RATING_BODY_NOT_SANITIZED to null

        val existing = ratingRepository.findById(ratingId) ?: return RatingOutcome.RATING_NOT_FOUND to null
        if (existing.raterId != raterId) return RatingOutcome.RATING_NOT_AUTHORIZED to null

        val nowMicros = Instant.now().toMicros()
        val strategy = eligibilityService.getStrategy(existing.contextType)

        val currentSupplements = existing.supplements ?: emptyList()
        if (currentSupplements.size >= strategy.maxSupplements()) {
            return RatingOutcome.RATING_SUPPLEMENT_LIMIT_REACHED to null
        }

        val cooldownEnd = maxOf(existing.editableUntil, lastSupplementOrFreezeTime(existing, currentSupplements)) +
            strategy.supplementCooldown().toMicrosPart()
        if (nowMicros < cooldownEnd) {
            return RatingOutcome.RATING_SUPPLEMENT_TOO_SOON to null
        }

        val supplementEditableUntil = Instant.now().plus(strategy.editWindow()).toMicros()
        val supplement = RatingSupplementDocument(
            id = UUIDv7.generate().toString(),
            body = body,
            status = RatingStatus.PUBLISHED.shortName,
            editableUntil = supplementEditableUntil,
            createdAt = nowMicros
        )

        ratingRepository.addSupplement(ratingId, supplement, nowMicros)

        val updated = ratingRepository.findById(ratingId)!!
        return RatingOutcome.RATING_SUPPLEMENT_ADDED to updated.toDomain()
    }

    fun editSupplement(
        ratingId: String,
        supplementId: String,
        raterId: String,
        body: String
    ): Pair<RatingOutcome, Rating?> {
        val bodyValidation = contentValidator.validateBody(body)
        if (!bodyValidation.isValid) return RatingOutcome.RATING_BODY_NOT_SANITIZED to null

        val existing = ratingRepository.findById(ratingId) ?: return RatingOutcome.RATING_NOT_FOUND to null
        if (existing.raterId != raterId) return RatingOutcome.RATING_NOT_AUTHORIZED to null

        val supplement = existing.supplements?.find { it.id == supplementId }
            ?: return RatingOutcome.RATING_NOT_FOUND to null

        val nowMicros = Instant.now().toMicros()
        if (nowMicros >= supplement.editableUntil) {
            return RatingOutcome.RATING_EDIT_WINDOW_CLOSED to null
        }

        val strategy = eligibilityService.getStrategy(existing.contextType)
        val newEditableUntil = Instant.now().plus(strategy.editWindow()).toMicros()

        ratingRepository.updateSupplement(ratingId, supplementId, body, newEditableUntil, nowMicros)

        val updated = ratingRepository.findById(ratingId)!!
        return RatingOutcome.RATING_UPDATED to updated.toDomain()
    }

    fun addResponse(
        ratingId: String,
        rateeId: String,
        body: String
    ): Pair<RatingOutcome, Rating?> {
        val bodyValidation = contentValidator.validateResponse(body)
        if (!bodyValidation.isValid) return RatingOutcome.RATING_BODY_NOT_SANITIZED to null

        val existing = ratingRepository.findById(ratingId) ?: return RatingOutcome.RATING_NOT_FOUND to null
        if (existing.rateeId != rateeId) return RatingOutcome.RATING_NOT_AUTHORIZED to null

        val nowMicros = Instant.now().toMicros()
        val response = RatingResponseDocument(
            body = body,
            createdAt = nowMicros,
            updatedAt = nowMicros
        )

        ratingRepository.setResponse(ratingId, response, nowMicros)

        val updated = ratingRepository.findById(ratingId)!!
        return RatingOutcome.RATING_RESPONSE_ADDED to updated.toDomain()
    }

    fun softDeleteRating(ratingId: String, raterId: String): Pair<RatingOutcome, Rating?> {
        val existing = ratingRepository.findById(ratingId) ?: return RatingOutcome.RATING_NOT_FOUND to null
        if (existing.raterId != raterId) return RatingOutcome.RATING_NOT_AUTHORIZED to null

        val nowMicros = Instant.now().toMicros()
        ratingRepository.updateStatus(ratingId, RatingStatus.DELETED.shortName, null, nowMicros)

        aggregateService.onRatingHidden(existing.rateeId, existing.repKind, existing.score)

        val updated = ratingRepository.findById(ratingId)!!
        return RatingOutcome.RATING_DELETED to updated.toDomain()
    }

    fun hideRating(ratingId: String): Pair<RatingOutcome, Rating?> {
        val existing = ratingRepository.findById(ratingId) ?: return RatingOutcome.RATING_NOT_FOUND to null
        if (existing.status == RatingStatus.HIDDEN.shortName) return RatingOutcome.RATING_ALREADY_HIDDEN to null

        val nowMicros = Instant.now().toMicros()
        ratingRepository.updateStatus(ratingId, RatingStatus.HIDDEN.shortName, null, nowMicros)

        aggregateService.onRatingHidden(existing.rateeId, existing.repKind, existing.score)

        log.info("Rating hidden by admin: id={}", ratingId)
        val updated = ratingRepository.findById(ratingId)!!
        return RatingOutcome.RATING_HIDDEN to updated.toDomain()
    }

    fun restoreRating(ratingId: String): Pair<RatingOutcome, Rating?> {
        val existing = ratingRepository.findById(ratingId) ?: return RatingOutcome.RATING_NOT_FOUND to null
        if (existing.status == RatingStatus.PUBLISHED.shortName) return RatingOutcome.RATING_NOT_FOUND to null

        val nowMicros = Instant.now().toMicros()
        ratingRepository.updateStatus(ratingId, RatingStatus.PUBLISHED.shortName, nowMicros, nowMicros)

        aggregateService.onRatingRestored(
            rateeId = existing.rateeId,
            repKind = existing.repKind,
            score = existing.score,
            ratingAt = nowMicros
        )

        log.info("Rating restored by admin: id={}", ratingId)
        val updated = ratingRepository.findById(ratingId)!!
        return RatingOutcome.RATING_RESTORED to updated.toDomain()
    }

    fun findById(id: String): Rating? = ratingRepository.findById(id)?.toDomain()

    fun findByRateePaginated(rateeId: String, repKind: String, pageable: Pageable): Page<Rating> =
        ratingRepository.findByRateePaginated(rateeId, repKind, pageable).map { it.toDomain() }

    fun findByRaterPaginated(raterId: String, pageable: Pageable): Page<Rating> =
        ratingRepository.findByRaterPaginated(raterId, pageable).map { it.toDomain() }

    fun findByRaterPublishedPaginated(raterId: String, pageable: Pageable): Page<Rating> =
        ratingRepository.findByRaterPublishedPaginated(raterId, pageable).map { it.toDomain() }

    fun findReportedRatings(pageable: Pageable): Page<Rating> =
        ratingRepository.findReportedRatings(pageable).map { it.toDomain() }

    private fun lastSupplementOrFreezeTime(
        existing: RatingDocument,
        supplements: List<RatingSupplementDocument>
    ): Long = if (supplements.isNotEmpty()) {
        supplements.maxOf { it.createdAt }
    } else {
        existing.editableUntil
    }

    private fun java.time.Duration.toMicrosPart(): Long =
        this.seconds * 1_000_000L + this.nano / 1_000L
}

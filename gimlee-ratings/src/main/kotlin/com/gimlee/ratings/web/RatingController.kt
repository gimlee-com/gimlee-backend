package com.gimlee.ratings.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.ratings.domain.RatingAggregateService
import com.gimlee.ratings.domain.RatingEligibilityService
import com.gimlee.ratings.domain.RatingService
import com.gimlee.ratings.web.dto.request.AddRatingResponseRequest
import com.gimlee.ratings.web.dto.request.AddSupplementRequest
import com.gimlee.ratings.web.dto.request.CreateRatingRequest
import com.gimlee.ratings.web.dto.request.EditRatingRequest
import com.gimlee.ratings.web.dto.response.EligibilityResponseDto
import com.gimlee.ratings.web.dto.response.RatingAggregateResponseDto
import com.gimlee.ratings.web.dto.response.RatingResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "Ratings", description = "Endpoints for user ratings and reviews")
@RestController
@RequestMapping("/ratings")
class RatingController(
    private val ratingService: RatingService,
    private val eligibilityService: RatingEligibilityService,
    private val aggregateService: RatingAggregateService,
    private val messageSource: MessageSource
) {

    @Operation(summary = "Create Rating", description = "Submit a new rating for a completed transaction. Consumes the matching eligibility.")
    @PostMapping
    @Privileged(role = "USER")
    fun createRating(@Valid @RequestBody request: CreateRatingRequest): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val (outcome, rating) = ratingService.createRating(
            contextType = request.contextType,
            contextId = request.contextId,
            raterId = userId,
            score = request.score,
            title = request.title,
            body = request.body,
            photoPaths = request.photoPaths
        )
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }

    @Operation(summary = "Edit Rating", description = "Edit an existing rating within the free-edit window.")
    @PatchMapping("/{ratingId}")
    @Privileged(role = "USER")
    fun editRating(
        @PathVariable ratingId: String,
        @Valid @RequestBody request: EditRatingRequest
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val (outcome, rating) = ratingService.editRating(
            ratingId = ratingId,
            raterId = userId,
            score = request.score,
            title = request.title,
            body = request.body,
            photoPaths = request.photoPaths
        )
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }

    @Operation(summary = "Delete Rating", description = "Soft-delete own rating.")
    @DeleteMapping("/{ratingId}")
    @Privileged(role = "USER")
    fun deleteRating(@PathVariable ratingId: String): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val (outcome, rating) = ratingService.softDeleteRating(ratingId, userId)
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }

    @Operation(summary = "Add Supplement", description = "Append a follow-up supplement to a frozen rating.")
    @PostMapping("/{ratingId}/supplements")
    @Privileged(role = "USER")
    fun addSupplement(
        @PathVariable ratingId: String,
        @Valid @RequestBody request: AddSupplementRequest
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val (outcome, rating) = ratingService.addSupplement(ratingId, userId, request.body)
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }

    @Operation(summary = "Edit Supplement", description = "Edit a supplement within its free-edit window.")
    @PatchMapping("/{ratingId}/supplements/{supplementId}")
    @Privileged(role = "USER")
    fun editSupplement(
        @PathVariable ratingId: String,
        @PathVariable supplementId: String,
        @Valid @RequestBody request: AddSupplementRequest
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val (outcome, rating) = ratingService.editSupplement(ratingId, supplementId, userId, request.body)
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }

    @Operation(summary = "Add Response", description = "Add a ratee response to a rating.")
    @PostMapping("/{ratingId}/response")
    @Privileged(role = "USER")
    fun addResponse(
        @PathVariable ratingId: String,
        @Valid @RequestBody request: AddRatingResponseRequest
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val (outcome, rating) = ratingService.addResponse(ratingId, userId, request.body)
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }

    @Operation(summary = "Get Rating", description = "Get a rating by ID.")
    @GetMapping("/public/{ratingId}")
    fun getRating(@PathVariable ratingId: String): ResponseEntity<Any> {
        val rating = ratingService.findById(ratingId)
            ?: return handleOutcome(com.gimlee.ratings.domain.RatingOutcome.RATING_NOT_FOUND)
        return handleOutcome(com.gimlee.common.domain.model.CommonOutcome.SUCCESS, RatingResponseDto.fromDomain(rating))
    }

    @Operation(summary = "Get Ratings Received", description = "Get paginated ratings received by a user, filtered by reputation kind.")
    @GetMapping("/user/{userId}")
    fun getRatingsReceived(
        @PathVariable userId: String,
        @Parameter(description = "Reputation kind (SEL or BUY)") @RequestParam(defaultValue = "SEL") repKind: String,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        val ratings = ratingService.findByRateePaginated(userId, repKind, PageRequest.of(page, size))
        val dtos = ratings.map { RatingResponseDto.fromDomain(it) }
        return ResponseEntity.ok(dtos)
    }

    @Operation(summary = "Get Ratings Written", description = "Get paginated ratings written by the authenticated user.")
    @GetMapping("/mine")
    @Privileged(role = "USER")
    fun getMyRatings(
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val ratings = ratingService.findByRaterPaginated(userId, PageRequest.of(page, size))
        val dtos = ratings.map { RatingResponseDto.fromDomain(it) }
        return ResponseEntity.ok(dtos)
    }

    @Operation(summary = "Get Pending Eligibility", description = "Get paginated pending rating eligibility for the authenticated user.")
    @GetMapping("/eligibility")
    @Privileged(role = "USER")
    fun getPendingEligibility(
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val eligibilities = eligibilityService.findPendingByRater(userId, PageRequest.of(page, size))
        val dtos = eligibilities.map { EligibilityResponseDto.fromDomain(it) }
        return ResponseEntity.ok(dtos)
    }

    @Operation(summary = "Get User Reputation", description = "Get aggregated reputation for a user by reputation kind.")
    @GetMapping("/aggregate/{userId}")
    fun getUserReputation(
        @PathVariable userId: String,
        @Parameter(description = "Reputation kind (SEL or BUY)") @RequestParam(defaultValue = "SEL") repKind: String
    ): ResponseEntity<Any> {
        val aggregate = aggregateService.findByRateeAndRepKind(userId, repKind)
            ?: return handleOutcome(com.gimlee.ratings.domain.RatingOutcome.RATING_NOT_FOUND)
        return handleOutcome(
            com.gimlee.common.domain.model.CommonOutcome.SUCCESS,
            RatingAggregateResponseDto.fromDomain(aggregate)
        )
    }

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }
}

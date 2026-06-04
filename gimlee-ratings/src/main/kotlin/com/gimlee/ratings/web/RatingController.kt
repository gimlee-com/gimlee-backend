package com.gimlee.ratings.web

import com.gimlee.auth.annotation.Privileged
import com.gimlee.auth.util.HttpServletRequestAuthUtil
import com.gimlee.common.domain.model.CommonOutcome
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.ratings.domain.RatingAggregateService
import com.gimlee.ratings.domain.RatingEligibilityService
import com.gimlee.ratings.domain.RatingOutcome
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
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
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

    @Operation(
        summary = "Create Rating",
        description = "Submit a new rating for a completed transaction. Consumes the matching eligibility."
    )
    @ApiResponse(
        responseCode = "201", description = "Rating created successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid input. Possible status codes: RATING_INVALID_SCORE, RATING_BODY_NOT_SANITIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "No pending eligibility found. Possible status codes: ELIGIBILITY_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Conflict. Possible status codes: RATING_ALREADY_EXISTS, RATING_DWELL_NOT_ELAPSED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
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

    @Operation(
        summary = "Edit Rating",
        description = "Edit an existing rating within the free-edit window. Slides the editableUntil timestamp forward."
    )
    @ApiResponse(
        responseCode = "200", description = "Rating updated successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid input. Possible status codes: RATING_INVALID_SCORE, RATING_BODY_NOT_SANITIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not the rating author. Possible status codes: RATING_NOT_AUTHORIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Rating not found. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Edit window has closed. Possible status codes: RATING_EDIT_WINDOW_CLOSED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
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

    @Operation(
        summary = "Delete Rating",
        description = "Soft-delete own rating. The rating status changes to DEL and the aggregate is decremented."
    )
    @ApiResponse(
        responseCode = "200", description = "Rating deleted successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not the rating author. Possible status codes: RATING_NOT_AUTHORIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Rating not found. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @DeleteMapping("/{ratingId}")
    @Privileged(role = "USER")
    fun deleteRating(@PathVariable ratingId: String): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val (outcome, rating) = ratingService.softDeleteRating(ratingId, userId)
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }

    @Operation(
        summary = "Add Supplement",
        description = "Append a follow-up supplement to a frozen rating. Requires the supplement cooldown to have elapsed and the supplement count to be below the maximum."
    )
    @ApiResponse(
        responseCode = "200", description = "Supplement added successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid content. Possible status codes: RATING_BODY_NOT_SANITIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not the rating author. Possible status codes: RATING_NOT_AUTHORIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Rating not found. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Cooldown or limit. Possible status codes: RATING_SUPPLEMENT_TOO_SOON, RATING_SUPPLEMENT_LIMIT_REACHED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
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

    @Operation(
        summary = "Edit Supplement",
        description = "Edit a supplement within its free-edit window."
    )
    @ApiResponse(
        responseCode = "200", description = "Rating updated successfully (supplement edited)",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid content. Possible status codes: RATING_BODY_NOT_SANITIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not the rating author. Possible status codes: RATING_NOT_AUTHORIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Rating or supplement not found. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Edit window closed. Possible status codes: RATING_EDIT_WINDOW_CLOSED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
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

    @Operation(
        summary = "Add Response",
        description = "Add a ratee response to a rating. Only the ratee (the user who received the rating) can respond."
    )
    @ApiResponse(
        responseCode = "200", description = "Response added successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid content. Possible status codes: RATING_BODY_NOT_SANITIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not the ratee. Possible status codes: RATING_NOT_AUTHORIZED",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Rating not found. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
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

    @Operation(
        summary = "Get Rating",
        description = "Get a single rating by ID. Public endpoint — returns the rating regardless of status."
    )
    @ApiResponse(
        responseCode = "200", description = "Rating found",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Rating not found. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/public/{ratingId}")
    fun getRating(@PathVariable ratingId: String): ResponseEntity<Any> {
        val rating = ratingService.findById(ratingId)
            ?: return handleOutcome(RatingOutcome.RATING_NOT_FOUND)
        return handleOutcome(CommonOutcome.SUCCESS, RatingResponseDto.fromDomain(rating))
    }

    @Operation(
        summary = "Get Ratings Received",
        description = "Get paginated published ratings received by a user, filtered by reputation kind (SEL for seller reputation, BUY for buyer reputation). Public endpoint for profile pages."
    )
    @ApiResponse(
        responseCode = "200", description = "Paginated list of ratings received",
        content = [Content(schema = Schema(implementation = Page::class))]
    )
    @GetMapping("/user/{userId}")
    fun getRatingsReceived(
        @PathVariable userId: String,
        @Parameter(description = "Reputation kind: SEL (seller) or BUY (buyer)") @RequestParam(defaultValue = "SEL") repKind: String,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        val ratings = ratingService.findByRateePaginated(userId, repKind, PageRequest.of(page, size))
        val dtos = ratings.map { RatingResponseDto.fromDomain(it) }
        return ResponseEntity.ok(dtos)
    }

    @Operation(
        summary = "Get Ratings Written by User (Public)",
        description = "Get paginated published ratings written by a specific user. Public endpoint for profile transparency — only returns PUBLISHED ratings."
    )
    @ApiResponse(
        responseCode = "200", description = "Paginated list of ratings written by the user",
        content = [Content(schema = Schema(implementation = Page::class))]
    )
    @GetMapping("/user/{userId}/written")
    fun getRatingsWrittenByUser(
        @PathVariable userId: String,
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        val ratings = ratingService.findByRaterPublishedPaginated(userId, PageRequest.of(page, size))
        val dtos = ratings.map { RatingResponseDto.fromDomain(it) }
        return ResponseEntity.ok(dtos)
    }

    @Operation(
        summary = "Get My Ratings Written",
        description = "Get paginated ratings written by the authenticated user. Returns all statuses (PUB, HID, DEL) — this is the author's own view."
    )
    @ApiResponse(
        responseCode = "200", description = "Paginated list of own ratings",
        content = [Content(schema = Schema(implementation = Page::class))]
    )
    @GetMapping("/mine")
    @Privileged(role = "USER")
    fun getMyRatings(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val ratings = ratingService.findByRaterPaginated(userId, PageRequest.of(page, size))
        val dtos = ratings.map { RatingResponseDto.fromDomain(it) }
        return ResponseEntity.ok(dtos)
    }

    @Operation(
        summary = "Get Pending Eligibility",
        description = "Get paginated pending rating eligibility for the authenticated user. Only returns PENDING eligibilities (not CONSUMED). Includes upcoming eligibilities that are still within the dwell period."
    )
    @ApiResponse(
        responseCode = "200", description = "Paginated list of pending eligibilities",
        content = [Content(schema = Schema(implementation = Page::class))]
    )
    @GetMapping("/eligibility")
    @Privileged(role = "USER")
    fun getPendingEligibility(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        val userId = HttpServletRequestAuthUtil.getPrincipal().userId
        val eligibilities = eligibilityService.findPendingByRater(userId, PageRequest.of(page, size))
        val dtos = eligibilities.map { EligibilityResponseDto.fromDomain(it) }
        return ResponseEntity.ok(dtos)
    }

    @Operation(
        summary = "Get User Reputation",
        description = "Get aggregated reputation statistics for a user by reputation kind (SEL for seller, BUY for buyer). Returns star count, average, distribution histogram, and last rating timestamp."
    )
    @ApiResponse(
        responseCode = "200", description = "Aggregated reputation data",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "No aggregate found for this user and reputation kind. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @GetMapping("/aggregate/{userId}")
    fun getUserReputation(
        @PathVariable userId: String,
        @Parameter(description = "Reputation kind: SEL (seller) or BUY (buyer)") @RequestParam(defaultValue = "SEL") repKind: String
    ): ResponseEntity<Any> {
        val aggregate = aggregateService.findByRateeAndRepKind(userId, repKind)
            ?: return handleOutcome(RatingOutcome.RATING_NOT_FOUND)
        return handleOutcome(
            CommonOutcome.SUCCESS,
            RatingAggregateResponseDto.fromDomain(aggregate)
        )
    }

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }
}

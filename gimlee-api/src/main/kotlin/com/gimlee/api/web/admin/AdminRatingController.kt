package com.gimlee.api.web.admin

import com.gimlee.auth.annotation.Privileged
import com.gimlee.common.domain.model.Outcome
import com.gimlee.common.web.dto.StatusResponseDto
import com.gimlee.ratings.domain.RatingService
import com.gimlee.ratings.web.dto.response.RatingResponseDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin Ratings", description = "Admin endpoints for rating moderation")
@RestController
@RequestMapping("/admin/ratings")
class AdminRatingController(
    private val ratingService: RatingService,
    private val messageSource: MessageSource
) {

    private fun handleOutcome(outcome: Outcome, data: Any? = null): ResponseEntity<Any> {
        val message = messageSource.getMessage(outcome.messageKey, null, LocaleContextHolder.getLocale())
        return ResponseEntity.status(outcome.httpCode).body(StatusResponseDto.fromOutcome(outcome, message, data))
    }

    @Operation(
        summary = "List reported ratings",
        description = "Fetches a paginated list of ratings that have received one or more moderation reports, sorted by report count descending."
    )
    @ApiResponse(responseCode = "200", description = "Paginated list of reported ratings")
    @GetMapping("/reported")
    @Privileged(role = "SUPPORT")
    fun listReportedRatings(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Any> {
        val ratings = ratingService.findReportedRatings(PageRequest.of(page, size))
        val dtos = ratings.map { RatingResponseDto.fromDomain(it) }
        return ResponseEntity.ok(dtos)
    }

    @Operation(
        summary = "Hide rating",
        description = "Hide a rating from public view (sets status to HID). Decrements the aggregate score. Use this for moderation actions."
    )
    @ApiResponse(
        responseCode = "200", description = "Rating hidden successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Rating not found. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "409",
        description = "Rating is already hidden. Possible status codes: RATING_ALREADY_HIDDEN",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/{ratingId}/hide")
    @Privileged(role = "SUPPORT")
    fun hideRating(@PathVariable ratingId: String): ResponseEntity<Any> {
        val (outcome, rating) = ratingService.hideRating(ratingId)
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }

    @Operation(
        summary = "Restore rating",
        description = "Restore a hidden or deleted rating back to published status. Re-increments the aggregate score."
    )
    @ApiResponse(
        responseCode = "200", description = "Rating restored successfully",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @ApiResponse(
        responseCode = "404",
        description = "Rating not found or already published. Possible status codes: RATING_NOT_FOUND",
        content = [Content(schema = Schema(implementation = StatusResponseDto::class))]
    )
    @PostMapping("/{ratingId}/restore")
    @Privileged(role = "SUPPORT")
    fun restoreRating(@PathVariable ratingId: String): ResponseEntity<Any> {
        val (outcome, rating) = ratingService.restoreRating(ratingId)
        return handleOutcome(outcome, rating?.let { RatingResponseDto.fromDomain(it) })
    }
}

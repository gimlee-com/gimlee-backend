package com.gimlee.ratings.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class CreateRatingRequest(
    @Schema(description = "Context type (e.g., ORDER)")
    val contextType: String,

    @Schema(description = "Context ID (e.g., purchaseId)")
    val contextId: String,

    @Schema(description = "Rating score (1-5 stars)")
    @field:Min(1)
    @field:Max(5)
    val score: Int,

    @Schema(description = "Optional short headline for the review")
    @field:Size(max = 200)
    val title: String? = null,

    @Schema(description = "Free-text review body (sanitized markdown)")
    @field:Size(max = 5000)
    val body: String? = null,

    @Schema(description = "Media-store paths for review photos")
    @field:Size(max = 5)
    val photoPaths: List<String>? = null
)

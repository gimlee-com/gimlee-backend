package com.gimlee.ratings.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddRatingResponseRequest(
    @Schema(description = "Response body (sanitized markdown)")
    @field:NotBlank
    @field:Size(max = 5000)
    val body: String
)

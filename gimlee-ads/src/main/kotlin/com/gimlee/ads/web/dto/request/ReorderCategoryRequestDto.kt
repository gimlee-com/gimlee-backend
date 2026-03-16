package com.gimlee.ads.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern

@Schema(description = "Request body for reordering a category among its siblings")
data class ReorderCategoryRequestDto(
    @Schema(description = "Direction to move the category", example = "UP", allowableValues = ["UP", "DOWN"])
    @field:Pattern(regexp = "UP|DOWN", message = "Direction must be UP or DOWN.")
    val direction: String
)

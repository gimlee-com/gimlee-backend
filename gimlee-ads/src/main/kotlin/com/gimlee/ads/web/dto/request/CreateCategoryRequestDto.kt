package com.gimlee.ads.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

@Schema(description = "Request body for creating a new category")
data class CreateCategoryRequestDto(
    @Schema(description = "Category names by language tag (e.g., en-US → 'Electronics')", example = """{"en-US": "Electronics", "pl-PL": "Elektronika"}""")
    @field:NotEmpty(message = "At least one category name is required.")
    val name: Map<String, @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters.") String>,

    @Schema(description = "ID of the parent category (null for root categories)", example = "42")
    val parentId: Int? = null
)

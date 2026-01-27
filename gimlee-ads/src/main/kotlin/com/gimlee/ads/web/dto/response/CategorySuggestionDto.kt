package com.gimlee.ads.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Represents a suggested category with its full breadcrumb path")
data class CategorySuggestionDto(
    @Schema(description = "The ID of the selectable (leaf) category")
    val id: Int,
    @Schema(description = "The full breadcrumb path from root to this category")
    val path: List<CategoryPathElementDto>,
    @Schema(description = "A pre-formatted display string of the path (e.g., 'Electronics > Computers > Laptops')")
    val displayPath: String
)

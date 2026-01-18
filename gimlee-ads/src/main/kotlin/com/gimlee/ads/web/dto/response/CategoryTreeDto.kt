package com.gimlee.ads.web.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Represents a category in a tree structure")
data class CategoryTreeDto(
    @Schema(description = "Unique ID of the category")
    val id: Int,
    @Schema(description = "Localized name of the category")
    val name: String,
    @Schema(description = "Slug of the category for URL use")
    val slug: String,
    @Schema(description = "Whether this category has subcategories")
    val hasChildren: Boolean,
    @Schema(description = "List of subcategories. Only populated if requested by depth.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val children: List<CategoryTreeDto>? = null
)

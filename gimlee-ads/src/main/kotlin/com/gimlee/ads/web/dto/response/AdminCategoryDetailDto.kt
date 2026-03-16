package com.gimlee.ads.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Detailed admin view of a single category")
data class AdminCategoryDetailDto(
    @Schema(description = "Unique ID of the category")
    val id: Int,

    @Schema(description = "Category names by language tag")
    val name: Map<String, AdminCategoryNameDto>,

    @Schema(description = "Whether this category has subcategories")
    val hasChildren: Boolean,

    @Schema(description = "Number of direct child categories")
    val childCount: Int,

    @Schema(description = "ID of the parent category (null for root categories)")
    val parentId: Int?,

    @Schema(description = "Sort order among siblings")
    val displayOrder: Int,

    @Schema(description = "Whether this category is hidden from the public API")
    val hidden: Boolean,

    @Schema(description = "Source type of the category (GPT or GML)")
    val sourceType: String,

    @Schema(description = "Source ID within the source system")
    val sourceId: String,

    @Schema(description = "Whether admin overrides have been applied to this category's name")
    val adminOverride: Boolean,

    @Schema(description = "Whether this category has been deprecated by the source sync")
    val deprecated: Boolean,

    @Schema(description = "Number of active ads using this category")
    val popularity: Long,

    @Schema(description = "Full path from root to this category")
    val path: List<CategoryPathElementDto>,

    @Schema(description = "Creation timestamp in epoch microseconds")
    val createdAt: Long,

    @Schema(description = "Last update timestamp in epoch microseconds")
    val updatedAt: Long
)

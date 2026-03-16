package com.gimlee.ads.web.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Admin view of a category in the tree structure")
data class AdminCategoryTreeDto(
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

    @Schema(description = "Number of active ads using this category")
    val popularity: Long,

    @Schema(description = "Creation timestamp in epoch microseconds")
    val createdAt: Long,

    @Schema(description = "Last update timestamp in epoch microseconds")
    val updatedAt: Long,

    @Schema(description = "List of subcategories. Only populated if requested by depth.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val children: List<AdminCategoryTreeDto>? = null
)

@Schema(description = "Category name and slug for a specific language")
data class AdminCategoryNameDto(
    @Schema(description = "Human-readable category name")
    val name: String,

    @Schema(description = "URL-friendly slug")
    val slug: String
)

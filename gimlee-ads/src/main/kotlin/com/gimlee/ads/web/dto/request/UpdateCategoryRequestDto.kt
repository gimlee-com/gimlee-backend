package com.gimlee.ads.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request body for updating a category")
data class UpdateCategoryRequestDto(
    @Schema(description = "Updated category names by language tag", example = """{"en-US": "Consumer Electronics"}""")
    val name: Map<String, String>? = null,

    @Schema(description = "Updated slugs by language tag (if not provided, slugs are auto-generated from name)", example = """{"en-US": "consumer-electronics"}""")
    val slug: Map<String, String>? = null,

    @Schema(description = "Whether to hide or show the category. Hiding cascades to all descendants.", example = "true")
    val hidden: Boolean? = null,

    @Schema(description = "Must be set to true to confirm hiding when active ads would be deactivated", example = "false")
    val acknowledge: Boolean = false
)

package com.gimlee.ads.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request body for moving a category to a new parent")
data class MoveCategoryRequestDto(
    @Schema(description = "ID of the new parent category (null to move to root)", example = "10")
    val newParentId: Int? = null
)

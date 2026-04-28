package com.gimlee.api.web.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Lightweight user summary for display in lists and detail views")
data class UserSummaryDto(
    @field:Schema(description = "User's display username")
    val username: String,
    @field:Schema(description = "URL of the user's avatar, if set")
    val avatarUrl: String?
)

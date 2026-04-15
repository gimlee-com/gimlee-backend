package com.gimlee.notifications.web.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Unread count data")
data class UnreadCountDataDto(
    @Schema(description = "The total number of unread notifications", example = "5")
    val count: Long
)

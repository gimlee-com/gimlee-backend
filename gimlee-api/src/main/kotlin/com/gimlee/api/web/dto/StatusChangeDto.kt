package com.gimlee.api.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "A single entry in a purchase's status change history")
data class StatusChangeDto(
    @field:Schema(description = "The status that the purchase transitioned to")
    val status: String,
    @field:Schema(description = "Timestamp when the transition occurred")
    val timestamp: Instant
)

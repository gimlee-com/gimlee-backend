package com.gimlee.api.web.dto

import com.gimlee.notifications.web.dto.NotificationListDto
import com.gimlee.notifications.web.dto.UnreadCountDataDto
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Standard API response for notification list")
data class NotificationListStatusResponseDto(
    @Schema(description = "Indicates if the operation was successful", example = "true")
    val success: Boolean,
    @Schema(description = "Machine-readable status slug", example = "SUCCESS")
    val status: String,
    @Schema(description = "Localized human-readable message", example = "Operation completed successfully.")
    val message: String? = null,
    @Schema(description = "The paged notifications and unread count")
    val data: NotificationListDto? = null
)

@Schema(description = "Standard API response for unread notification count")
data class UnreadCountStatusResponseDto(
    @Schema(description = "Indicates if the operation was successful", example = "true")
    val success: Boolean,
    @Schema(description = "Machine-readable status slug", example = "SUCCESS")
    val status: String,
    @Schema(description = "Localized human-readable message", example = "Operation completed successfully.")
    val message: String? = null,
    @Schema(description = "The unread count object")
    val data: UnreadCountDataDto? = null
)

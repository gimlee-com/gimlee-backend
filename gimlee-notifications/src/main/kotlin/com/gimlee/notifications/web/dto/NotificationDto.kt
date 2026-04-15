package com.gimlee.notifications.web.dto

import com.gimlee.notifications.domain.model.*
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Represents an in-app notification")
data class NotificationDto(
    @Schema(description = "Unique ID of the notification", example = "019483...")
    val id: String,
    @Schema(description = "Machine-readable slug representing the notification type", example = "order.new")
    val type: String,
    @Schema(description = "Category of the notification", example = "orders")
    val category: String,
    @Schema(description = "Severity level of the notification", example = "info")
    val severity: String,
    @Schema(description = "Localized title of the notification", example = "New Order")
    val title: String,
    @Schema(description = "Localized content of the notification", example = "You have received a new order for 2x Widget.")
    val message: String,
    @Schema(description = "Whether the notification has been read")
    val read: Boolean,
    @Schema(description = "Timestamp when the notification was created (epoch microseconds)", example = "1744566780000000")
    val createdAt: Long,
    @Schema(description = "Action suggested to the user")
    val suggestedAction: SuggestedAction?,
    @Schema(description = "Additional metadata associated with the notification")
    val metadata: Map<String, String>?
) {
    companion object {
        fun from(notification: Notification): NotificationDto = NotificationDto(
            id = notification.id,
            type = notification.type.slug,
            category = notification.category.name.lowercase(),
            severity = notification.severity.name.lowercase(),
            title = notification.title,
            message = notification.message,
            read = notification.read,
            createdAt = notification.createdAt,
            suggestedAction = notification.suggestedAction,
            metadata = notification.metadata
        )
    }
}

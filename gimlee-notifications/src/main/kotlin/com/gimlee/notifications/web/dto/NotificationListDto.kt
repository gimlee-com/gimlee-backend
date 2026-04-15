package com.gimlee.notifications.web.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Paged list of notifications for a user")
data class NotificationListDto(
    @Schema(description = "List of notification objects")
    val notifications: List<NotificationDto>,
    @Schema(description = "Whether more notifications are available for pagination")
    val hasMore: Boolean,
    @Schema(description = "The total number of unread notifications for the user")
    val unreadCount: Long
)

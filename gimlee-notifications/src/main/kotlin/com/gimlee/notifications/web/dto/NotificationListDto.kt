package com.gimlee.notifications.web.dto

data class NotificationListDto(
    val notifications: List<NotificationDto>,
    val hasMore: Boolean,
    val unreadCount: Long
)

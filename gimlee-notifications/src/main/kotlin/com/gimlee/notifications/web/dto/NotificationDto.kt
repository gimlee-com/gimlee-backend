package com.gimlee.notifications.web.dto

import com.gimlee.notifications.domain.model.Notification

data class NotificationDto(
    val id: String,
    val type: String,
    val category: String,
    val severity: String,
    val title: String,
    val message: String,
    val read: Boolean,
    val createdAt: Long,
    val actionUrl: String?,
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
            actionUrl = notification.actionUrl,
            metadata = notification.metadata
        )
    }
}

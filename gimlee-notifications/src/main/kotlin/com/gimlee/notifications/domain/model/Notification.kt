package com.gimlee.notifications.domain.model

data class Notification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val category: NotificationCategory,
    val severity: NotificationSeverity,
    val title: String,
    val message: String,
    val read: Boolean = false,
    val suggestedAction: SuggestedAction? = null,
    val metadata: Map<String, String>? = null,
    val createdAt: Long
)

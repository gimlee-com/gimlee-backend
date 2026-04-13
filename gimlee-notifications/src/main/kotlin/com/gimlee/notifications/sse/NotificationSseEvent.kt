package com.gimlee.notifications.sse

import com.gimlee.notifications.domain.model.Notification

sealed class NotificationSseEvent {
    data class Created(val notification: Notification) : NotificationSseEvent()
    data class Read(val notificationId: String) : NotificationSseEvent()
    data class AllRead(val category: String?) : NotificationSseEvent()
    data class UnreadCountChanged(val count: Long) : NotificationSseEvent()
}

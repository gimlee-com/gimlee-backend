package com.gimlee.notifications.domain

import com.gimlee.common.domain.model.Outcome

enum class NotificationOutcome(override val httpCode: Int) : Outcome {
    NOTIFICATION_NOT_FOUND(404),
    NOTIFICATION_ACCESS_DENIED(403),
    NOTIFICATIONS_MARKED_READ(200),
    NOTIFICATION_MARKED_READ(200);

    override val code: String get() = name
    override val messageKey: String get() = "status.notifications.${name.lowercase().replace('_', '-')}"
}

package com.gimlee.notifications.domain.model

enum class NotificationCategory(val shortName: String) {
    ORDERS("O"),
    MESSAGES("M"),
    ADS("A"),
    QA("QA"),
    SUPPORT("S"),
    ACCOUNT("AC");

    companion object {
        private val shortNameMap = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): NotificationCategory =
            shortNameMap[shortName] ?: throw IllegalArgumentException("Unknown NotificationCategory shortName: $shortName")
    }
}

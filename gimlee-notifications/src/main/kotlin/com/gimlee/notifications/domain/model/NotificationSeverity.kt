package com.gimlee.notifications.domain.model

enum class NotificationSeverity(val shortName: String) {
    INFO("I"),
    SUCCESS("S"),
    WARNING("W"),
    DANGER("D");

    companion object {
        private val shortNameMap = entries.associateBy { it.shortName }
        fun fromShortName(shortName: String): NotificationSeverity =
            shortNameMap[shortName] ?: throw IllegalArgumentException("Unknown NotificationSeverity shortName: $shortName")
    }
}

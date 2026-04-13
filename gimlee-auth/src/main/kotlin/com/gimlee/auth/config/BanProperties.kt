package com.gimlee.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gimlee.auth.ban")
data class BanProperties(
    val expiryReminder: ExpiryReminderProperties = ExpiryReminderProperties()
) {
    data class ExpiryReminderProperties(
        val windowHours: Long = 24
    )
}

package com.gimlee.user.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gimlee.user.presence")
data class UserPresenceProperties(
    /**
     * How often to flush the activity buffer to the database (in milliseconds).
     */
    val flushIntervalMs: Long = 60000,

    /**
     * Threshold for considering a user as "Online" (in minutes).
     */
    val onlineThresholdMinutes: Long = 5
)

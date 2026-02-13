package com.gimlee.events

/**
 * Generic event for tracking analytics across the platform.
 */
data class GenericAnalyticsEvent(
    val type: String,
    val targetId: String? = null,
    val timestampMicros: Long,
    val sampleRate: Double = 1.0,
    val userId: String? = null,
    val clientId: String? = null,
    val botScore: Double? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

package com.gimlee.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gimlee.analytics")
data class AnalyticsProperties(
    val enabled: Boolean = true,
    val botScoreThreshold: Double = 0.7,
    val clientIdHeader: String = "X-Client-Id",
    val botScoreHeader: String = "X-Crowdsec-Bot-Score"
)

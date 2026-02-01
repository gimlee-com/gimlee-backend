package com.gimlee.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gimlee.ads.discovery")
data class AdDiscoveryProperties(
    /**
     * Number of other ads from the same user to display in ad details.
     */
    val otherAdsCount: Int = 5
)

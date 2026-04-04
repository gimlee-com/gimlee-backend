package com.gimlee.location.geoip.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gimlee.location.geoip")
data class GeoIpProperties(
    val enabled: Boolean = true,
    val licenseKey: String? = null,
    val databasePath: String? = null,
    val downloadUrl: String = "https://download.maxmind.com/app/geoip_download",
    val editionId: String = "GeoLite2-Country",
    val connectTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 60_000
)

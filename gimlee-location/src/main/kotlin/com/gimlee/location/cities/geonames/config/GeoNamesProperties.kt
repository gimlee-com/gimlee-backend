package com.gimlee.location.cities.geonames.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "gimlee.location.geonames")
data class GeoNamesProperties(
    val enabled: Boolean = true,
    val baseUrl: String = "https://download.geonames.org/export/dump",
    val citiesFile: String = "cities500.zip",
    val alternateNamesFile: String = "alternateNamesV2.zip",
    val admin1File: String = "admin1CodesASCII.txt",
    val admin2File: String = "admin2Codes.txt",
    val indexPath: String = "\${java.io.tmpdir}/gimlee/geonames-index",
    val syncCron: String = "0 0 3 * * *",
    val downloadTimeout: Duration = Duration.ofSeconds(300),
    val connectTimeout: Duration = Duration.ofSeconds(30),
    val batchSize: Int = 5000,
    val maxSuggestions: Int = 10,
    val supportedLanguages: Set<String> = setOf(
        "en", "es", "fr", "de", "it", "pt", "nl", "pl", "ru", "uk",
        "ja", "zh", "ko", "ar", "hi", "tr", "sv", "no", "da", "fi"
    ),
    val populationBoostThresholds: List<PopulationBoostThreshold> = listOf(
        PopulationBoostThreshold(10_000, 1.5f),
        PopulationBoostThreshold(100_000, 3.0f),
        PopulationBoostThreshold(1_000_000, 6.0f)
    ),
    val lock: LockProperties = LockProperties()
) {
    data class PopulationBoostThreshold(
        val threshold: Long,
        val boost: Float
    )

    data class LockProperties(
        val atMost: Duration = Duration.ofHours(1),
        val atLeast: Duration = Duration.ofMinutes(5)
    )
}

package com.gimlee.location.cities.geonames.sync

import com.gimlee.location.cities.geonames.config.GeoNamesProperties
import com.gimlee.location.cities.geonames.download.GeoNamesDownloader
import com.gimlee.location.cities.geonames.index.CitySearchIndex
import com.gimlee.location.cities.geonames.parser.GeoNameAdminDivision
import com.gimlee.location.cities.geonames.parser.GeoNamesParser
import com.gimlee.location.cities.persistence.CityRepository
import com.gimlee.location.cities.persistence.model.GeoNamesSyncMetadata.SyncStatus
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class GeoNamesSyncScheduler(
    private val properties: GeoNamesProperties,
    private val downloader: GeoNamesDownloader,
    private val parser: GeoNamesParser,
    private val cityRepository: CityRepository,
    private val citySearchIndex: CitySearchIndex
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        if (!properties.enabled) {
            log.info("GeoNames sync is disabled")
            return
        }

        // Try to load existing Lucene index first
        if (citySearchIndex.tryLoadExistingIndex()) {
            log.info("GeoNames city search index loaded from disk")
            return
        }

        // Check if MongoDB has data — if so, rebuild Lucene from it
        val metadata = cityRepository.getSyncMetadata()
        if (metadata != null && metadata.status == SyncStatus.SUCCESS && metadata.cityCount > 0) {
            log.info("Rebuilding Lucene index from MongoDB data ({} cities)", metadata.cityCount)
            try {
                citySearchIndex.rebuildIndex()
                return
            } catch (e: Exception) {
                log.warn("Failed to rebuild index from MongoDB: {}", e.message)
            }
        }

        // No data at all — trigger initial full sync
        log.info("No city data found — triggering initial GeoNames sync")
        Thread { performFullSync() }.apply {
            name = "geonames-initial-sync"
            isDaemon = true
            start()
        }
    }

    @Scheduled(cron = "\${gimlee.location.geonames.sync-cron:0 0 3 * * *}")
    @SchedulerLock(
        name = "geonames-sync",
        lockAtMostFor = "\${gimlee.location.geonames.lock.at-most:PT1H}",
        lockAtLeastFor = "\${gimlee.location.geonames.lock.at-least:PT5M}"
    )
    fun scheduledSync() {
        if (!properties.enabled) return
        performFullSync()
    }

    private fun performFullSync() {
        val tempDir = Files.createTempDirectory("geonames-sync-")
        log.info("Starting GeoNames full sync (temp dir: {})", tempDir)

        try {
            cityRepository.updateSyncMetadata(0, 0, 0, SyncStatus.IN_PROGRESS)

            // Download all files
            val citiesFile = downloader.downloadAndExtract(properties.citiesFile, tempDir)
            val alternateNamesFile = downloader.downloadAndExtract(properties.alternateNamesFile, tempDir)
            val admin1File = downloader.downloadPlainFile(properties.admin1File, tempDir)
            val admin2File = downloader.downloadPlainFile(properties.admin2File, tempDir)

            // Parse admin code files → lookup maps
            val admin1Map = parser.parseAdminCodes(admin1File)
            val admin2Map = parser.parseAdminCodes(admin2File)
            log.info("Parsed {} admin1 codes, {} admin2 codes", admin1Map.size, admin2Map.size)

            // Parse & load city IDs first
            val cityIds = parser.loadCityIds(citiesFile)
            log.info("Found {} cities in {}", cityIds.size, properties.citiesFile)

            // Build expanded geonameId filter: cities + referenced admin geonameIds
            val referencedAdminGids = collectReferencedAdminGids(citiesFile, admin1Map, admin2Map)
            val allGeonameIds = cityIds + referencedAdminGids
            log.info("Alternate names filter: {} city IDs + {} admin geonameIds = {} total",
                cityIds.size, referencedAdminGids.size, allGeonameIds.size)

            // Upsert cities (with admin name resolution)
            val cityCount = cityRepository.bulkUpsertCities(
                parser.parseCities(citiesFile),
                properties.batchSize,
                admin1Map,
                admin2Map
            )

            // Upsert filtered alternate names (includes admin translations)
            val nameCount = cityRepository.bulkUpsertAlternateNames(
                parser.parseAlternateNames(alternateNamesFile, allGeonameIds),
                properties.batchSize
            )

            // Rebuild Lucene index
            val indexedCount = citySearchIndex.rebuildIndex()

            val version = System.currentTimeMillis()
            cityRepository.updateSyncMetadata(cityCount, nameCount, version, SyncStatus.SUCCESS)

            log.info(
                "GeoNames sync completed: {} cities, {} alternate names, {} indexed",
                cityCount, nameCount, indexedCount
            )
        } catch (e: Exception) {
            log.error("GeoNames sync failed", e)
            try {
                cityRepository.updateSyncMetadata(
                    cityRepository.getCityCount(),
                    cityRepository.getNameCount(),
                    0,
                    SyncStatus.FAILED
                )
            } catch (metaEx: Exception) {
                log.error("Failed to update sync metadata after error", metaEx)
            }
        } finally {
            cleanupTempDir(tempDir)
        }
    }

    /**
     * Collects admin geonameIds that are actually referenced by cities in the dataset.
     * This avoids importing translations for admin divisions that have no cities.
     */
    private fun collectReferencedAdminGids(
        citiesFile: Path,
        admin1Map: Map<String, GeoNameAdminDivision>,
        admin2Map: Map<String, GeoNameAdminDivision>
    ): Set<String> {
        val adminGids = mutableSetOf<String>()
        parser.parseCities(citiesFile).forEach { city ->
            city.admin1Code?.let { adm1 ->
                admin1Map["${city.countryCode}.$adm1"]?.geonameId?.let { adminGids.add(it) }
            }
            if (city.admin1Code != null && city.admin2Code != null) {
                admin2Map["${city.countryCode}.${city.admin1Code}.${city.admin2Code}"]
                    ?.geonameId?.let { adminGids.add(it) }
            }
        }
        return adminGids
    }

    private fun cleanupTempDir(dir: Path) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        } catch (e: Exception) {
            log.warn("Failed to clean up temp dir {}: {}", dir, e.message)
        }
    }
}

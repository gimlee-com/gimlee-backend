package com.gimlee.location.cities.persistence.model

/**
 * MongoDB document tracking the state of GeoNames data synchronization.
 * Collection: gimlee-location-sync-metadata
 */
data class GeoNamesSyncMetadata(
    val id: String = SINGLETON_ID,
    val lastSyncMicros: Long,
    val cityCount: Long,
    val nameCount: Long,
    val indexedVersion: Long,
    val status: SyncStatus
) {
    enum class SyncStatus(val shortName: String) {
        SUCCESS("OK"),
        FAILED("FAIL"),
        IN_PROGRESS("PROG");

        companion object {
            fun fromShortName(shortName: String): SyncStatus =
                entries.first { it.shortName == shortName }
        }
    }

    companion object {
        const val SINGLETON_ID = "geonames-sync"
        const val COLLECTION_NAME = "gimlee-location-sync-metadata"
        const val FIELD_ID = "_id"
        const val FIELD_LAST_SYNC = "ls"
        const val FIELD_CITY_COUNT = "cc"
        const val FIELD_NAME_COUNT = "nc"
        const val FIELD_INDEXED_VERSION = "iv"
        const val FIELD_STATUS = "st"
    }
}

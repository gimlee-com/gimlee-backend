package com.gimlee.location.cities.persistence.model

/**
 * MongoDB document representing a city from GeoNames.
 * Collection: gimlee-location-cities
 */
data class CityDocument(
    val id: String,
    val nm: String,
    val ascii: String,
    val cc: String,
    val adm1: String?,
    val adm2: String?,
    val adm1Nm: String?,
    val adm2Nm: String?,
    val adm1Gid: String?,
    val adm2Gid: String?,
    val lat: Double,
    val lon: Double,
    val pop: Long,
    val tz: String?,
    val mod: Long
) {
    companion object {
        const val COLLECTION_NAME = "gimlee-location-cities"
        const val FIELD_ID = "_id"
        const val FIELD_NAME = "nm"
        const val FIELD_ASCII_NAME = "ascii"
        const val FIELD_COUNTRY_CODE = "cc"
        const val FIELD_ADMIN1 = "adm1"
        const val FIELD_ADMIN2 = "adm2"
        const val FIELD_ADMIN1_NAME = "adm1Nm"
        const val FIELD_ADMIN2_NAME = "adm2Nm"
        const val FIELD_ADMIN1_GID = "adm1Gid"
        const val FIELD_ADMIN2_GID = "adm2Gid"
        const val FIELD_LATITUDE = "lat"
        const val FIELD_LONGITUDE = "lon"
        const val FIELD_POPULATION = "pop"
        const val FIELD_TIMEZONE = "tz"
        const val FIELD_MODIFICATION = "mod"
    }
}

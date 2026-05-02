package com.gimlee.location.cities.persistence.model

/**
 * MongoDB document representing an alternate name for a city.
 * Collection: gimlee-location-city-names
 */
data class CityNameDocument(
    val id: String,
    val cid: String,
    val lang: String,
    val nm: String,
    val pref: Boolean,
    val shrt: Boolean
) {
    companion object {
        const val COLLECTION_NAME = "gimlee-location-city-names"
        const val FIELD_ID = "_id"
        const val FIELD_CITY_ID = "cid"
        const val FIELD_LANGUAGE = "lang"
        const val FIELD_NAME = "nm"
        const val FIELD_PREFERRED = "pref"
        const val FIELD_SHORT = "shrt"
    }
}

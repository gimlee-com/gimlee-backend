package com.gimlee.location.cities.geonames.parser

/**
 * Parsed city record from GeoNames cities500.txt.
 * GeoNames TSV columns: geonameid, name, asciiname, alternatenames, latitude, longitude,
 * feature class, feature code, country code, cc2, admin1 code, admin2 code,
 * admin3 code, admin4 code, population, elevation, dem, timezone, modification date
 */
data class GeoNameCity(
    val geonameId: String,
    val name: String,
    val asciiName: String,
    val countryCode: String,
    val admin1Code: String?,
    val admin2Code: String?,
    val latitude: Double,
    val longitude: Double,
    val population: Long,
    val timezone: String?,
    val modificationDate: String
)

/**
 * Parsed admin division record from admin1CodesASCII.txt or admin2Codes.txt.
 * Format: code(tab)name(tab)asciiName(tab)geonameId
 */
data class GeoNameAdminDivision(
    val code: String,
    val name: String,
    val asciiName: String,
    val geonameId: String
)

/**
 * Parsed alternate name record from GeoNames alternateNamesV2.txt.
 * Columns: alternateNameId, geonameid, isolanguage, alternate name,
 * isPreferredName, isShortName, isColloquial, isHistoric, from, to
 */
data class GeoNameAlternateName(
    val alternateNameId: String,
    val geonameId: String,
    val isoLanguage: String,
    val alternateName: String,
    val isPreferredName: Boolean,
    val isShortName: Boolean,
    val isColloquial: Boolean,
    val isHistoric: Boolean
)

package com.gimlee.location.cities.geonames.parser

import com.gimlee.location.cities.geonames.config.GeoNamesProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path

@Component
class GeoNamesParser(private val properties: GeoNamesProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val VALID_LANGUAGE_REGEX = Regex("^[a-z]{2,3}$")
    }

    fun parseCities(filePath: Path): Sequence<GeoNameCity> {
        log.info("Parsing cities from {}", filePath)
        return Files.newBufferedReader(filePath).lineSequence()
            .mapNotNull { line -> parseCityLine(line) }
    }

    fun parseAlternateNames(
        filePath: Path,
        cityIds: Set<String>
    ): Sequence<GeoNameAlternateName> {
        log.info("Parsing alternate names from {} (filtering to {} city IDs)", filePath, cityIds.size)
        return Files.newBufferedReader(filePath).lineSequence()
            .mapNotNull { line -> parseAlternateNameLine(line, cityIds) }
    }

    /**
     * Parses admin division codes (admin1CodesASCII.txt or admin2Codes.txt).
     * Format: code(tab)name(tab)asciiName(tab)geonameId
     * Returns map keyed by code (e.g., "PL.78" or "PL.78.1465").
     */
    fun parseAdminCodes(filePath: Path): Map<String, GeoNameAdminDivision> {
        log.info("Parsing admin codes from {}", filePath)
        val result = mutableMapOf<String, GeoNameAdminDivision>()
        Files.newBufferedReader(filePath).useLines { lines ->
            lines.forEach { line ->
                val fields = line.split('\t')
                if (fields.size >= 4) {
                    val code = fields[0]
                    if (code.isNotBlank() && fields[3].isNotBlank()) {
                        result[code] = GeoNameAdminDivision(
                            code = code,
                            name = fields[1],
                            asciiName = fields[2],
                            geonameId = fields[3]
                        )
                    }
                }
            }
        }
        log.info("Parsed {} admin codes from {}", result.size, filePath)
        return result
    }

    /**
     * Loads all city geonameIds from the cities file for filtering alternate names.
     */
    fun loadCityIds(filePath: Path): Set<String> {
        return Files.newBufferedReader(filePath).useLines { lines ->
            lines.mapNotNull { line ->
                val firstTab = line.indexOf('\t')
                if (firstTab > 0) line.substring(0, firstTab) else null
            }.toSet()
        }
    }

    private fun parseCityLine(line: String): GeoNameCity? {
        val fields = line.split('\t')
        if (fields.size < 19) return null

        return try {
            GeoNameCity(
                geonameId = fields[0],
                name = fields[1],
                asciiName = fields[2],
                countryCode = fields[8],
                admin1Code = fields[10].ifBlank { null },
                admin2Code = fields[11].ifBlank { null },
                latitude = fields[4].toDouble(),
                longitude = fields[5].toDouble(),
                population = fields[14].toLongOrNull() ?: 0L,
                timezone = fields[17].ifBlank { null },
                modificationDate = fields[18]
            )
        } catch (e: Exception) {
            log.debug("Skipping malformed city line: {}", e.message)
            null
        }
    }

    private fun parseAlternateNameLine(
        line: String,
        cityIds: Set<String>
    ): GeoNameAlternateName? {
        val fields = line.split('\t')
        if (fields.size < 8) return null

        val geonameId = fields[1]
        if (geonameId !in cityIds) return null

        val isoLanguage = fields[2]
        if (!isValidLanguageCode(isoLanguage)) return null

        val isColloquial = fields[6] == "1"
        val isHistoric = fields[7] == "1"
        if (isColloquial || isHistoric) return null

        return GeoNameAlternateName(
            alternateNameId = fields[0],
            geonameId = geonameId,
            isoLanguage = isoLanguage,
            alternateName = fields[3],
            isPreferredName = fields[4] == "1",
            isShortName = fields[5] == "1",
            isColloquial = false,
            isHistoric = false
        )
    }

    private fun isValidLanguageCode(code: String): Boolean {
        return VALID_LANGUAGE_REGEX.matches(code)
    }
}

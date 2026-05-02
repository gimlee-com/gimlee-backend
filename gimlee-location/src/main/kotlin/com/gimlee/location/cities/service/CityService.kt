package com.gimlee.location.cities.service

import com.gimlee.location.cities.geonames.index.CitySearchIndex
import com.gimlee.location.cities.geonames.index.CitySearchResult
import com.gimlee.location.cities.persistence.CityRepository
import com.gimlee.location.cities.persistence.model.CityDocument
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class CityService(
    private val cityRepository: CityRepository,
    private val citySearchIndex: CitySearchIndex
) {
    private val cityCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofHours(24))
        .build<String, CityDocument?>()

    // Cache for admin name localization: key = "$geonameId:$lang", value = localized name
    private val adminNameCache = Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(Duration.ofHours(24))
        .build<String, String?>()

    fun isReady(): Boolean = citySearchIndex.isReady()

    fun getCityById(id: String): CityDocument? {
        return cityCache.get(id) { cityRepository.getCityById(it) }
    }

    fun getCitiesByIds(ids: Collection<String>): Map<String, CityDocument> {
        val result = mutableMapOf<String, CityDocument>()
        val missingIds = mutableListOf<String>()

        for (id in ids) {
            val cached = cityCache.getIfPresent(id)
            if (cached != null) {
                result[id] = cached
            } else {
                missingIds.add(id)
            }
        }

        if (missingIds.isNotEmpty()) {
            val fromDb = cityRepository.getCitiesByIds(missingIds)
            fromDb.forEach { (id, city) ->
                cityCache.put(id, city)
                result[id] = city
            }
        }

        return result
    }

    fun search(
        phrase: String,
        countryCode: String?,
        languageTag: String?,
        limit: Int
    ): List<CitySearchResult> {
        return citySearchIndex.search(phrase, countryCode, languageTag, limit)
    }

    fun getLocalizedCityName(cityId: String, languageTag: String?): String? {
        if (languageTag == null) return getCityById(cityId)?.nm

        val lang = extractLanguage(languageTag)
        val names = cityRepository.getAlternateNames(cityId, lang)
        val preferredName = names.firstOrNull { it.pref }?.nm
            ?: names.firstOrNull()?.nm

        return preferredName ?: getCityById(cityId)?.nm
    }

    fun resolveDisplayName(cityId: String, defaultName: String, languageTag: String?): String {
        if (languageTag == null) return defaultName
        return getLocalizedCityName(cityId, languageTag) ?: defaultName
    }

    /**
     * Resolves localized admin division name for a given admin geonameId.
     * Falls back to the default (GeoNames native) name if no translation exists.
     */
    fun resolveLocalizedAdminName(adminGeonameId: String?, defaultName: String?, languageTag: String?): String? {
        if (adminGeonameId == null) return defaultName
        if (languageTag == null) return defaultName

        val lang = extractLanguage(languageTag)
        val cacheKey = "$adminGeonameId:$lang"

        return adminNameCache.get(cacheKey) {
            val names = cityRepository.getAlternateNames(adminGeonameId, lang)
            names.firstOrNull { it.pref }?.nm ?: names.firstOrNull()?.nm
        } ?: defaultName
    }

    /**
     * Batch-resolves localized admin names for search results.
     * Returns a map of geonameId → localized name.
     */
    fun batchResolveAdminNames(
        adminGeonameIds: Set<String>,
        languageTag: String?
    ): Map<String, String> {
        if (languageTag == null || adminGeonameIds.isEmpty()) return emptyMap()

        val lang = extractLanguage(languageTag)
        val result = mutableMapOf<String, String>()
        val toFetch = mutableSetOf<String>()

        for (gid in adminGeonameIds) {
            val cacheKey = "$gid:$lang"
            val cached = adminNameCache.getIfPresent(cacheKey)
            if (cached != null) {
                result[gid] = cached
            } else {
                toFetch.add(gid)
            }
        }

        if (toFetch.isNotEmpty()) {
            val fromDb = cityRepository.getAlternateNamesByGeonameIds(toFetch, lang)
            for (gid in toFetch) {
                val names = fromDb[gid] ?: continue
                val best = names.firstOrNull { it.pref }?.nm ?: names.firstOrNull()?.nm ?: continue
                val cacheKey = "$gid:$lang"
                adminNameCache.put(cacheKey, best)
                result[gid] = best
            }
        }

        return result
    }

    private fun extractLanguage(languageTag: String): String {
        val dashIndex = languageTag.indexOf('-')
        return if (dashIndex > 0) languageTag.substring(0, dashIndex).lowercase()
        else languageTag.lowercase()
    }
}

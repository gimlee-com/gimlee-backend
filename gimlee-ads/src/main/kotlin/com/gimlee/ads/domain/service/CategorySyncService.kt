package com.gimlee.ads.domain.service

import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.UUIDv7
import com.gimlee.common.toMicros
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.Instant
import java.util.*
import java.util.regex.Pattern

@Service
class CategorySyncService(
    private val categoryRepository: CategoryRepository,
    private val taxonomyDownloader: TaxonomyDownloader,
    @Value("\${gimlee.ads.gpt.url-template:https://www.google.com/basepages/producttype/taxonomy-with-ids.%s.txt}")
    private val gptUrlTemplate: String,
    @Value("\${gimlee.ads.gpt.languages:en-US,pl-PL}")
    private val languages: List<String>
) {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(CategorySyncService::class.java)
        private val NON_LATIN = Pattern.compile("[^\\w-]")
        private val WHITESPACE = Pattern.compile("[\\s]")
    }

    @Scheduled(cron = "\${gimlee.ads.category.sync.cron:0 0 0 1 * ?}")
    @SchedulerLock(name = "gptCategorySync", lockAtMostFor = "\${gimlee.ads.category.sync.lock.at-most:PT1H}", lockAtLeastFor = "\${gimlee.ads.category.sync.lock.at-least:PT5M}")
    fun syncCategories() {
        LOGGER.info("Starting Google Product Taxonomy sync")
        val now = Instant.now().toMicros()

        // 1. Load existing map
        val existingMap = categoryRepository.getGptSourceIdToUuidMap()
        val sourceIdToUuid = existingMap.toMutableMap()

        // 2. Download and Parse taxonomies for all configured languages
        val languageMaps = mutableMapOf<String, Map<String, ParsedCategory>>()

        languages.forEach { lang ->
            val url = String.format(gptUrlTemplate, lang)
            try {
                val taxonomyLines = taxonomyDownloader.download(url)
                val parsed = parseTaxonomy(taxonomyLines)
                languageMaps[lang] = parsed.associateBy { it.sourceId }
                LOGGER.debug("Downloaded and parsed {} categories for language: {}", parsed.size, lang)
            } catch (e: Exception) {
                LOGGER.error("Failed to download or parse taxonomy for language: $lang", e)
            }
        }

        if (languageMaps.isEmpty()) {
            LOGGER.warn("No taxonomies downloaded, skipping sync")
            return
        }

        // Use en-US as base for structure if available, otherwise just pick one
        val baseLanguage = if (languageMaps.containsKey("en-US")) "en-US" else languageMaps.keys.first()
        val baseMap = languageMaps[baseLanguage]!!

        // 3. Ensure all source IDs have a UUID
        baseMap.keys.forEach { sourceId ->
            sourceIdToUuid.getOrPut(sourceId) { UUIDv7.generate() }
        }

        // 4. Upsert
        val usedSlugsPerLanguage = languageMaps.keys.associateWith { mutableSetOf<String>() }

        baseMap.values.forEach { baseCategory ->
            val sourceId = baseCategory.sourceId
            val uuid = sourceIdToUuid[sourceId]!!
            val parentUuid = baseCategory.parentSourceId?.let { sourceIdToUuid[it] }

            val nameMap = mutableMapOf<String, Category.CategoryName>()

            languageMaps.forEach { (lang, map) ->
                val categoryInLang = map[sourceId]
                if (categoryInLang != null) {
                    val usedSlugs = usedSlugsPerLanguage[lang]!!
                    val originalSlug = slugify(categoryInLang.name)
                    var slug = originalSlug
                    var counter = 2
                    while (usedSlugs.contains(slug)) {
                        slug = "$originalSlug-$counter"
                        counter++
                    }
                    usedSlugs.add(slug)
                    nameMap[lang] = Category.CategoryName(categoryInLang.name, slug)
                }
            }

            if (nameMap.isNotEmpty()) {
                categoryRepository.upsertGptCategory(uuid, sourceId, parentUuid, nameMap, now)
            }
        }

        // 5. Deprecate missing
        categoryRepository.deprecateMissingGptCategories(now)

        LOGGER.info("Google Product Taxonomy sync completed successfully")
    }

    private data class ParsedCategory(
        val sourceId: String,
        val fullPath: String,
        val name: String,
        val parentSourceId: String?
    )

    private fun parseTaxonomy(lines: List<String>): List<ParsedCategory> {
        // Pass 1: Collect IDs and FullNames
        val nameToId = mutableMapOf<String, String>()
        val tempParsed = mutableListOf<Pair<String, String>>() // id, fullName

        lines.forEach { line ->
            if (line.startsWith("#") || line.isBlank()) return@forEach

            // Format: ID - Name
            val parts = line.split(" - ", limit = 2)
            if (parts.size != 2) return@forEach

            val id = parts[0].trim()
            val fullName = parts[1].trim()

            nameToId[fullName] = id
            tempParsed.add(id to fullName)
        }

        // Pass 2: Resolve parents
        val result = mutableListOf<ParsedCategory>()

        tempParsed.forEach { (id, fullName) ->
            // Determine parent
            // Parent is everything before the last " > "
            val lastSeparator = fullName.lastIndexOf(" > ")
            var parentId: String? = null
            var name = fullName

            if (lastSeparator > 0) {
                val parentName = fullName.substring(0, lastSeparator)
                parentId = nameToId[parentName]
                name = fullName.substring(lastSeparator + 3)
            }

            result.add(ParsedCategory(id, fullName, name, parentId))
        }
        return result
    }

    private fun slugify(input: String): String {
        val nowhitespace = WHITESPACE.matcher(input).replaceAll("-")
        val withSpecialReplaced = nowhitespace.replace("ł", "l").replace("Ł", "L")
        val normalized = Normalizer.normalize(withSpecialReplaced, Normalizer.Form.NFD)
        val slug = NON_LATIN.matcher(normalized).replaceAll("")
        return slug.lowercase(Locale.ENGLISH)
            .replace(Regex("-+"), "-")
            .trim('-')
    }
}

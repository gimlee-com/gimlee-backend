package com.gimlee.ads.domain.service

import com.gimlee.ads.domain.model.Category
import com.gimlee.ads.persistence.CategoryRepository
import com.gimlee.common.toMicros
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
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
    private val messageSource: MessageSource,
    @Value("\${gimlee.ads.gpt.url-template:https://www.google.com/basepages/producttype/taxonomy-with-ids.%s.txt}")
    private val gptUrlTemplate: String,
    @Value("\${gimlee.ads.gpt.languages:en-US,pl-PL}")
    private val languages: List<String>,
    @Value("\${gimlee.ads.category.sync.enabled:false}")
    private val enabled: Boolean
) {

    @Autowired
    @Lazy
    private lateinit var self: CategorySyncService

    companion object {
        private val LOGGER = LoggerFactory.getLogger(CategorySyncService::class.java)
        private val NON_LATIN = Pattern.compile("[^\\w-]")
        private val WHITESPACE = Pattern.compile("[\\s]")
    }

    @EventListener(ApplicationReadyEvent::class)
    fun syncOnStartup() {
        if (enabled && !categoryRepository.hasAnyCategoryOfSourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)) {
            LOGGER.info("No GPT categories found in database. Triggering initial sync.")
            if (::self.isInitialized) {
                self.syncCategories()
            } else {
                syncCategories()
            }
        }
    }

    @Scheduled(cron = "\${gimlee.ads.category.sync.cron:0 0 0 1 * ?}")
    @SchedulerLock(name = "gptCategorySync", lockAtMostFor = "\${gimlee.ads.category.sync.lock.at-most:PT1H}", lockAtLeastFor = "\${gimlee.ads.category.sync.lock.at-least:PT5M}")
    fun syncCategories() {
        if (!enabled) {
            LOGGER.debug("Google Product Taxonomy sync is disabled")
            return
        }
        LOGGER.info("Starting Google Product Taxonomy sync")
        val now = Instant.now().toMicros()

        // 1. Load existing map
        val existingMap = categoryRepository.getSourceIdToIdMapBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY)
        val sourceIdToId = existingMap.toMutableMap()

        // 2. Download and Parse taxonomies for all configured languages
        val languageMaps = mutableMapOf<String, MutableMap<String, ParsedCategory>>()

        languages.forEach { lang ->
            val url = String.format(gptUrlTemplate, lang)
            try {
                val taxonomyLines = taxonomyDownloader.download(url)
                val parsed = parseTaxonomy(taxonomyLines)
                languageMaps[lang] = parsed.associateBy { it.sourceId }.toMutableMap()
                LOGGER.debug("Downloaded and parsed {} categories for language: {}", parsed.size, lang)
            } catch (e: Exception) {
                LOGGER.error("Failed to download or parse taxonomy for language: $lang", e)
            }
        }

        if (languageMaps.isEmpty()) {
            LOGGER.warn("No taxonomies downloaded, skipping sync")
            return
        }

        // Enrich with "Miscellaneous" children for non-leaf nodes
        val baseLanguage = if (languageMaps.containsKey("en-US")) "en-US" else languageMaps.keys.first()
        val baseMapForStructure = languageMaps[baseLanguage]!!
        val nonLeafSourceIds = baseMapForStructure.values.mapNotNull { it.parentSourceId }.toSet()

        nonLeafSourceIds.forEach { parentId ->
            val miscSourceId = "$parentId-misc"
            languages.forEach { lang ->
                val langMap = languageMaps[lang] ?: return@forEach
                val parentInLang = langMap[parentId] ?: return@forEach
                val miscName = messageSource.getMessage("category.gpt.miscellaneous", null, Locale.forLanguageTag(lang))
                val miscFullPath = "${parentInLang.fullPath} > $miscName"
                langMap[miscSourceId] = ParsedCategory(miscSourceId, miscFullPath, miscName, parentId)
            }
        }

        val baseMap = languageMaps[baseLanguage]!!

        // 3. Ensure all source IDs have an ID
        val usedIds = sourceIdToId.values.toMutableSet()
        var nextId = (usedIds.maxOrNull() ?: categoryRepository.getMaxId()) + 1

        baseMap.keys.forEach { sourceId ->
            if (!sourceIdToId.containsKey(sourceId)) {
                while (usedIds.contains(nextId)) {
                    nextId++
                }
                sourceIdToId[sourceId] = nextId
                usedIds.add(nextId)
                nextId++
            }
        }

        // 4. Upsert
        val usedSlugsPerLanguage = languageMaps.keys.associateWith { mutableSetOf<String>() }

        baseMap.values.forEach { baseCategory ->
            val sourceId = baseCategory.sourceId
            val id = sourceIdToId[sourceId]!!
            val parentId = baseCategory.parentSourceId?.let { sourceIdToId[it] }

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
                categoryRepository.upsertCategoryBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, id, sourceId, parentId, nameMap, now)
            }
        }

        // 5. Deprecate missing
        categoryRepository.deprecateMissingCategoriesBySourceType(Category.Source.Type.GOOGLE_PRODUCT_TAXONOMY, now)

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
        val withAnd = input.replace("&", " and ")
        val nowhitespace = WHITESPACE.matcher(withAnd).replaceAll("-")
        val withSpecialReplaced = nowhitespace.replace("ł", "l").replace("Ł", "L")
        val normalized = Normalizer.normalize(withSpecialReplaced, Normalizer.Form.NFD)
        val slug = NON_LATIN.matcher(normalized).replaceAll("")
        return slug.lowercase(Locale.ENGLISH)
            .replace(Regex("-+"), "-")
            .trim('-')
    }
}

package com.gimlee.location.cities.geonames.index

import com.gimlee.location.cities.geonames.config.GeoNamesProperties
import com.gimlee.location.cities.persistence.CityRepository
import com.gimlee.location.cities.persistence.model.CityDocument
import com.gimlee.location.cities.persistence.model.CityNameDocument
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.LowerCaseFilter
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.Tokenizer
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter
import org.apache.lucene.analysis.standard.StandardTokenizer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import jakarta.annotation.PreDestroy

@Component
class CitySearchIndex(
    private val properties: GeoNamesProperties,
    private val cityRepository: CityRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val FIELD_GEONAME_ID = "geonameId"
        const val FIELD_NAME = "name"
        const val FIELD_ASCII_NAME = "asciiName"
        const val FIELD_ALL_NAMES = "allNames"
        const val FIELD_COUNTRY_CODE = "countryCode"
        const val FIELD_POPULATION = "population"
        const val FIELD_POPULATION_SORT = "populationSort"
        const val FIELD_LATITUDE = "lat"
        const val FIELD_LONGITUDE = "lon"
        const val FIELD_DEFAULT_NAME = "defaultName"
        const val FIELD_ADMIN1_NAME = "admin1Name"
        const val FIELD_ADMIN2_NAME = "admin2Name"
        const val FIELD_ADMIN1_GID = "admin1Gid"
        const val FIELD_ADMIN2_GID = "admin2Gid"
        private const val ALT_NAME_PREFIX = "altName_"

        fun altNameField(lang: String): String = "$ALT_NAME_PREFIX$lang"
    }

    private val indexPath: Path = Path.of(properties.indexPath)
    private val searcherRef = AtomicReference<IndexSearcher?>()
    private val ready = AtomicBoolean(false)

    val analyzer: Analyzer = createAnalyzer()

    fun isReady(): Boolean = ready.get()

    fun rebuildIndex(): Long {
        log.info("Rebuilding Lucene city index at {}", indexPath)
        Files.createDirectories(indexPath)

        val directory = FSDirectory.open(indexPath)
        val config = IndexWriterConfig(analyzer)
        config.openMode = IndexWriterConfig.OpenMode.CREATE

        var count = 0L

        // First, collect all alternate names grouped by city ID
        val namesByCityId = mutableMapOf<String, MutableList<CityNameDocument>>()
        cityRepository.streamAllAlternateNames().forEach { name ->
            namesByCityId.getOrPut(name.cid) { mutableListOf() }.add(name)
        }
        log.info("Loaded alternate names for {} cities", namesByCityId.size)

        IndexWriter(directory, config).use { writer ->
            cityRepository.streamAllCities().forEach { city ->
                val names = namesByCityId[city.id] ?: emptyList()
                val doc = buildLuceneDocument(city, names)
                writer.addDocument(doc)
                count++

                if (count % 50_000 == 0L) {
                    log.info("Indexed {} cities...", count)
                }
            }
            writer.commit()
        }

        // Open a new searcher
        val reader = DirectoryReader.open(directory)
        val newSearcher = IndexSearcher(reader)

        val oldSearcher = searcherRef.getAndSet(newSearcher)
        ready.set(true)

        oldSearcher?.indexReader?.close()

        log.info("Lucene index rebuilt with {} cities", count)
        return count
    }

    fun tryLoadExistingIndex(): Boolean {
        if (!Files.exists(indexPath)) return false

        return try {
            val directory = FSDirectory.open(indexPath)
            if (!DirectoryReader.indexExists(directory)) {
                directory.close()
                return false
            }
            val reader = DirectoryReader.open(directory)
            searcherRef.set(IndexSearcher(reader))
            ready.set(true)
            log.info("Loaded existing Lucene index with {} documents", reader.numDocs())
            true
        } catch (e: Exception) {
            log.warn("Failed to load existing Lucene index: {}", e.message)
            false
        }
    }

    fun search(
        phrase: String,
        countryCode: String?,
        languageTag: String?,
        limit: Int
    ): List<CitySearchResult> {
        val searcher = searcherRef.get() ?: return emptyList()

        val lang = languageTag?.let { extractLanguage(it) }
        val textQuery = buildTextQuery(phrase, lang)
        val boostedQuery = buildPopulationBoostedQuery(textQuery)

        val finalQuery = if (countryCode != null) {
            BooleanQuery.Builder()
                .add(boostedQuery, BooleanClause.Occur.MUST)
                .add(TermQuery(Term(FIELD_COUNTRY_CODE, countryCode)), BooleanClause.Occur.FILTER)
                .build()
        } else {
            boostedQuery
        }

        val topDocs = searcher.search(finalQuery, limit)

        return topDocs.scoreDocs.map { scoreDoc ->
            val doc = searcher.storedFields().document(scoreDoc.doc)
            CitySearchResult(
                geonameId = doc.get(FIELD_GEONAME_ID),
                defaultName = doc.get(FIELD_DEFAULT_NAME),
                countryCode = doc.get(FIELD_COUNTRY_CODE),
                latitude = doc.get(FIELD_LATITUDE).toDouble(),
                longitude = doc.get(FIELD_LONGITUDE).toDouble(),
                population = doc.getField(FIELD_POPULATION_SORT)?.numericValue()?.toLong() ?: 0L,
                score = scoreDoc.score,
                localizedName = lang?.let { resolveLocalizedName(doc, it) },
                admin1Name = doc.get(FIELD_ADMIN1_NAME),
                admin2Name = doc.get(FIELD_ADMIN2_NAME),
                admin1GeonameId = doc.get(FIELD_ADMIN1_GID),
                admin2GeonameId = doc.get(FIELD_ADMIN2_GID)
            )
        }
    }

    private fun buildTextQuery(phrase: String, lang: String?): Query {
        val normalizedPhrase = phrase.lowercase().trim()
        val builder = BooleanQuery.Builder()

        // Prefix queries on core fields
        builder.add(PrefixQuery(Term(FIELD_NAME, normalizedPhrase)), BooleanClause.Occur.SHOULD)
        builder.add(PrefixQuery(Term(FIELD_ASCII_NAME, normalizedPhrase)), BooleanClause.Occur.SHOULD)
        builder.add(PrefixQuery(Term(FIELD_ALL_NAMES, normalizedPhrase)), BooleanClause.Occur.SHOULD)

        // Boost the user's language field if available
        if (lang != null && lang in properties.supportedLanguages) {
            val langQuery = BoostQuery(
                PrefixQuery(Term(altNameField(lang), normalizedPhrase)),
                2.0f
            )
            builder.add(langQuery, BooleanClause.Occur.SHOULD)
        }

        builder.setMinimumNumberShouldMatch(1)
        return builder.build()
    }

    private fun buildPopulationBoostedQuery(textQuery: Query): Query {
        val sortedThresholds = properties.populationBoostThresholds.sortedByDescending { it.threshold }

        var query: Query = textQuery
        for (threshold in sortedThresholds) {
            val popFilter = LongPoint.newRangeQuery(
                FIELD_POPULATION,
                threshold.threshold,
                Long.MAX_VALUE
            )
            val boosted = BoostQuery(
                BooleanQuery.Builder()
                    .add(textQuery, BooleanClause.Occur.MUST)
                    .add(popFilter, BooleanClause.Occur.FILTER)
                    .build(),
                threshold.boost
            )
            query = BooleanQuery.Builder()
                .add(query, BooleanClause.Occur.SHOULD)
                .add(boosted, BooleanClause.Occur.SHOULD)
                .build()
        }

        return query
    }

    private fun resolveLocalizedName(doc: org.apache.lucene.document.Document, lang: String): String? {
        return doc.get(altNameField(lang))
    }

    private fun buildLuceneDocument(
        city: CityDocument,
        alternateNames: List<CityNameDocument>
    ): org.apache.lucene.document.Document {
        val doc = org.apache.lucene.document.Document()

        // Stored + searchable ID
        doc.add(StringField(FIELD_GEONAME_ID, city.id, Field.Store.YES))

        // Text fields for search
        doc.add(TextField(FIELD_NAME, city.nm, Field.Store.NO))
        doc.add(TextField(FIELD_ASCII_NAME, city.ascii, Field.Store.NO))

        // Stored default name for display fallback
        doc.add(StoredField(FIELD_DEFAULT_NAME, city.nm))

        // All names field for broad matching
        val allNamesBuilder = StringBuilder()
        allNamesBuilder.append(city.nm).append(' ').append(city.ascii)

        // Per-language alternate name fields (only for supported languages)
        val namesByLang = alternateNames.groupBy { it.lang }
        for ((lang, names) in namesByLang) {
            val preferredName = names.firstOrNull { it.pref }?.nm
                ?: names.firstOrNull()?.nm
                ?: continue

            if (lang in properties.supportedLanguages) {
                doc.add(TextField(altNameField(lang), preferredName, Field.Store.YES))
            }

            // Add all alternate names to allNames for broad matching
            names.forEach { allNamesBuilder.append(' ').append(it.nm) }
        }

        doc.add(TextField(FIELD_ALL_NAMES, allNamesBuilder.toString(), Field.Store.NO))

        // Country code for filtering
        doc.add(StringField(FIELD_COUNTRY_CODE, city.cc, Field.Store.YES))

        // Population for boosting
        doc.add(LongPoint(FIELD_POPULATION, city.pop))
        doc.add(NumericDocValuesField(FIELD_POPULATION_SORT, city.pop))
        doc.add(StoredField(FIELD_POPULATION_SORT, city.pop))

        // Coordinates (stored for response)
        doc.add(StoredField(FIELD_LATITUDE, city.lat))
        doc.add(StoredField(FIELD_LONGITUDE, city.lon))

        // Admin division names and geonameIds (stored for display, not indexed for search)
        city.adm1Nm?.let { doc.add(StoredField(FIELD_ADMIN1_NAME, it)) }
        city.adm2Nm?.let { doc.add(StoredField(FIELD_ADMIN2_NAME, it)) }
        city.adm1Gid?.let { doc.add(StoredField(FIELD_ADMIN1_GID, it)) }
        city.adm2Gid?.let { doc.add(StoredField(FIELD_ADMIN2_GID, it)) }

        return doc
    }

    private fun extractLanguage(languageTag: String): String {
        val dashIndex = languageTag.indexOf('-')
        return if (dashIndex > 0) languageTag.substring(0, dashIndex).lowercase()
        else languageTag.lowercase()
    }

    private fun createAnalyzer(): Analyzer {
        return object : Analyzer() {
            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer: Tokenizer = StandardTokenizer()
                var stream: TokenStream = LowerCaseFilter(tokenizer)
                stream = ASCIIFoldingFilter(stream)
                return TokenStreamComponents(tokenizer, stream)
            }
        }
    }

    @PreDestroy
    fun close() {
        searcherRef.getAndSet(null)?.indexReader?.close()
    }
}

data class CitySearchResult(
    val geonameId: String,
    val defaultName: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val population: Long,
    val score: Float,
    val localizedName: String?,
    val admin1Name: String? = null,
    val admin2Name: String? = null,
    val admin1GeonameId: String? = null,
    val admin2GeonameId: String? = null
) {
    val displayName: String get() = localizedName ?: defaultName
}

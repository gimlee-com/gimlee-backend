package com.gimlee.location.cities.persistence

import com.gimlee.common.toMicros
import com.gimlee.location.cities.geonames.parser.GeoNameAdminDivision
import com.gimlee.location.cities.geonames.parser.GeoNameAlternateName
import com.gimlee.location.cities.geonames.parser.GeoNameCity
import com.gimlee.location.cities.persistence.model.CityDocument
import com.gimlee.location.cities.persistence.model.CityNameDocument
import com.gimlee.location.cities.persistence.model.GeoNamesSyncMetadata
import com.gimlee.location.cities.persistence.model.GeoNamesSyncMetadata.SyncStatus
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Repository
class CityRepository(private val mongoDatabase: MongoDatabase) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val citiesCollection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(CityDocument.COLLECTION_NAME)
    }

    private val namesCollection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(CityNameDocument.COLLECTION_NAME)
    }

    private val metadataCollection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(GeoNamesSyncMetadata.COLLECTION_NAME)
    }

    fun getCityById(id: String): CityDocument? {
        val doc = citiesCollection.find(Filters.eq(CityDocument.FIELD_ID, id)).first()
            ?: return null
        return documentToCity(doc)
    }

    fun getCitiesByIds(ids: Collection<String>): Map<String, CityDocument> {
        if (ids.isEmpty()) return emptyMap()
        return citiesCollection.find(Filters.`in`(CityDocument.FIELD_ID, ids))
            .map { doc -> documentToCity(doc) }
            .associateBy { it.id }
    }

    fun getAlternateNames(cityId: String, language: String): List<CityNameDocument> {
        return namesCollection.find(
            Filters.and(
                Filters.eq(CityNameDocument.FIELD_CITY_ID, cityId),
                Filters.eq(CityNameDocument.FIELD_LANGUAGE, language)
            )
        ).map { documentToName(it) }.toList()
    }

    fun getAlternateNamesForCities(
        cityIds: Collection<String>,
        language: String
    ): Map<String, List<CityNameDocument>> {
        if (cityIds.isEmpty()) return emptyMap()
        return namesCollection.find(
            Filters.and(
                Filters.`in`(CityNameDocument.FIELD_CITY_ID, cityIds),
                Filters.eq(CityNameDocument.FIELD_LANGUAGE, language)
            )
        ).map { documentToName(it) }.groupBy { it.cid }
    }

    /**
     * Batch lookup of alternate names for multiple geonameIds (cities or admin divisions).
     * Returns a map of geonameId → list of alternate names for the given language.
     */
    fun getAlternateNamesByGeonameIds(
        geonameIds: Set<String>,
        language: String
    ): Map<String, List<CityNameDocument>> {
        if (geonameIds.isEmpty()) return emptyMap()
        return namesCollection.find(
            Filters.and(
                Filters.`in`(CityNameDocument.FIELD_CITY_ID, geonameIds.toList()),
                Filters.eq(CityNameDocument.FIELD_LANGUAGE, language)
            )
        ).map { documentToName(it) }.groupBy { it.cid }
    }

    fun bulkUpsertCities(
        cities: Sequence<GeoNameCity>,
        batchSize: Int,
        admin1Map: Map<String, GeoNameAdminDivision> = emptyMap(),
        admin2Map: Map<String, GeoNameAdminDivision> = emptyMap()
    ): Long {
        var count = 0L
        val upsertOption = ReplaceOptions().upsert(true)

        cities.chunked(batchSize).forEach { batch ->
            batch.forEach { city ->
                val doc = cityToBsonDocument(city, admin1Map, admin2Map)
                citiesCollection.replaceOne(
                    Filters.eq(CityDocument.FIELD_ID, city.geonameId),
                    doc,
                    upsertOption
                )
                count++
            }
            if (count % 50_000 == 0L) {
                log.info("Upserted {} cities...", count)
            }
        }

        log.info("Upserted {} cities total", count)
        return count
    }

    fun bulkUpsertAlternateNames(names: Sequence<GeoNameAlternateName>, batchSize: Int): Long {
        var count = 0L
        val upsertOption = ReplaceOptions().upsert(true)

        names.chunked(batchSize).forEach { batch ->
            batch.forEach { name ->
                val doc = nameToBsonDocument(name)
                namesCollection.replaceOne(
                    Filters.eq(CityNameDocument.FIELD_ID, name.alternateNameId),
                    doc,
                    upsertOption
                )
                count++
            }
            if (count % 100_000 == 0L) {
                log.info("Upserted {} alternate names...", count)
            }
        }

        log.info("Upserted {} alternate names total", count)
        return count
    }

    fun getCityCount(): Long = citiesCollection.countDocuments()

    fun getNameCount(): Long = namesCollection.countDocuments()

    fun getSyncMetadata(): GeoNamesSyncMetadata? {
        val doc = metadataCollection.find(
            Filters.eq(GeoNamesSyncMetadata.FIELD_ID, GeoNamesSyncMetadata.SINGLETON_ID)
        ).first() ?: return null

        return GeoNamesSyncMetadata(
            lastSyncMicros = doc.getLong(GeoNamesSyncMetadata.FIELD_LAST_SYNC),
            cityCount = doc.getLong(GeoNamesSyncMetadata.FIELD_CITY_COUNT),
            nameCount = doc.getLong(GeoNamesSyncMetadata.FIELD_NAME_COUNT),
            indexedVersion = doc.getLong(GeoNamesSyncMetadata.FIELD_INDEXED_VERSION),
            status = SyncStatus.fromShortName(doc.getString(GeoNamesSyncMetadata.FIELD_STATUS))
        )
    }

    fun updateSyncMetadata(
        cityCount: Long,
        nameCount: Long,
        indexedVersion: Long,
        status: SyncStatus
    ) {
        metadataCollection.updateOne(
            Filters.eq(GeoNamesSyncMetadata.FIELD_ID, GeoNamesSyncMetadata.SINGLETON_ID),
            Updates.combine(
                Updates.set(GeoNamesSyncMetadata.FIELD_LAST_SYNC, Instant.now().toMicros()),
                Updates.set(GeoNamesSyncMetadata.FIELD_CITY_COUNT, cityCount),
                Updates.set(GeoNamesSyncMetadata.FIELD_NAME_COUNT, nameCount),
                Updates.set(GeoNamesSyncMetadata.FIELD_INDEXED_VERSION, indexedVersion),
                Updates.set(GeoNamesSyncMetadata.FIELD_STATUS, status.shortName)
            ),
            UpdateOptions().upsert(true)
        )
    }

    /**
     * Streams all cities from MongoDB. Used for Lucene index rebuild.
     */
    fun streamAllCities(): Sequence<CityDocument> {
        return citiesCollection.find().asSequence().map { documentToCity(it) }
    }

    /**
     * Streams all alternate names from MongoDB. Used for Lucene index rebuild.
     */
    fun streamAllAlternateNames(): Sequence<CityNameDocument> {
        return namesCollection.find().asSequence().map { documentToName(it) }
    }

    private fun cityToBsonDocument(
        city: GeoNameCity,
        admin1Map: Map<String, GeoNameAdminDivision>,
        admin2Map: Map<String, GeoNameAdminDivision>
    ): Document {
        val modMicros = try {
            LocalDate.parse(city.modificationDate, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toMicros()
        } catch (_: Exception) {
            Instant.now().toMicros()
        }

        // Resolve admin division names using fully-qualified keys
        val admin1Key = city.admin1Code?.let { "${city.countryCode}.$it" }
        val admin1 = admin1Key?.let { admin1Map[it] }

        val admin2Key = if (city.admin1Code != null && city.admin2Code != null)
            "${city.countryCode}.${city.admin1Code}.${city.admin2Code}" else null
        val admin2 = admin2Key?.let { admin2Map[it] }

        return Document()
            .append(CityDocument.FIELD_ID, city.geonameId)
            .append(CityDocument.FIELD_NAME, city.name)
            .append(CityDocument.FIELD_ASCII_NAME, city.asciiName)
            .append(CityDocument.FIELD_COUNTRY_CODE, city.countryCode)
            .append(CityDocument.FIELD_ADMIN1, city.admin1Code)
            .append(CityDocument.FIELD_ADMIN2, city.admin2Code)
            .append(CityDocument.FIELD_ADMIN1_NAME, admin1?.name)
            .append(CityDocument.FIELD_ADMIN2_NAME, admin2?.name)
            .append(CityDocument.FIELD_ADMIN1_GID, admin1?.geonameId)
            .append(CityDocument.FIELD_ADMIN2_GID, admin2?.geonameId)
            .append(CityDocument.FIELD_LATITUDE, city.latitude)
            .append(CityDocument.FIELD_LONGITUDE, city.longitude)
            .append(CityDocument.FIELD_POPULATION, city.population)
            .append(CityDocument.FIELD_TIMEZONE, city.timezone)
            .append(CityDocument.FIELD_MODIFICATION, modMicros)
    }

    private fun nameToBsonDocument(name: GeoNameAlternateName): Document {
        return Document()
            .append(CityNameDocument.FIELD_ID, name.alternateNameId)
            .append(CityNameDocument.FIELD_CITY_ID, name.geonameId)
            .append(CityNameDocument.FIELD_LANGUAGE, name.isoLanguage)
            .append(CityNameDocument.FIELD_NAME, name.alternateName)
            .append(CityNameDocument.FIELD_PREFERRED, name.isPreferredName)
            .append(CityNameDocument.FIELD_SHORT, name.isShortName)
    }

    private fun documentToCity(doc: Document): CityDocument {
        return CityDocument(
            id = doc.getString(CityDocument.FIELD_ID),
            nm = doc.getString(CityDocument.FIELD_NAME),
            ascii = doc.getString(CityDocument.FIELD_ASCII_NAME),
            cc = doc.getString(CityDocument.FIELD_COUNTRY_CODE),
            adm1 = doc.getString(CityDocument.FIELD_ADMIN1),
            adm2 = doc.getString(CityDocument.FIELD_ADMIN2),
            adm1Nm = doc.getString(CityDocument.FIELD_ADMIN1_NAME),
            adm2Nm = doc.getString(CityDocument.FIELD_ADMIN2_NAME),
            adm1Gid = doc.getString(CityDocument.FIELD_ADMIN1_GID),
            adm2Gid = doc.getString(CityDocument.FIELD_ADMIN2_GID),
            lat = doc.getDouble(CityDocument.FIELD_LATITUDE),
            lon = doc.getDouble(CityDocument.FIELD_LONGITUDE),
            pop = doc.getLong(CityDocument.FIELD_POPULATION),
            tz = doc.getString(CityDocument.FIELD_TIMEZONE),
            mod = doc.getLong(CityDocument.FIELD_MODIFICATION)
        )
    }

    private fun documentToName(doc: Document): CityNameDocument {
        return CityNameDocument(
            id = doc.getString(CityNameDocument.FIELD_ID),
            cid = doc.getString(CityNameDocument.FIELD_CITY_ID),
            lang = doc.getString(CityNameDocument.FIELD_LANGUAGE),
            nm = doc.getString(CityNameDocument.FIELD_NAME),
            pref = doc.getBoolean(CityNameDocument.FIELD_PREFERRED, false),
            shrt = doc.getBoolean(CityNameDocument.FIELD_SHORT, false)
        )
    }
}

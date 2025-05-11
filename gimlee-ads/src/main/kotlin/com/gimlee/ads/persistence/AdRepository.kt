package com.gimlee.ads.persistence

import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction
import com.gimlee.ads.model.AdStatus
import com.gimlee.ads.model.Currency
import com.gimlee.ads.persistence.model.AdDocument
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.geo.GeoJsonPoint
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class AdRepository(mongoDatabase: MongoDatabase) {

    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-ads"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-advertisements"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    /**
     * Finds an AdDocument by its ID.
     */
    fun findById(id: ObjectId): AdDocument? {
        val query = Filters.eq(AdDocument.FIELD_ID, id)
        return collection.find(query).limit(1).firstOrNull()?.let { mapToAdDocument(it) }
    }

    /**
     * Saves a new AdDocument or replaces an existing one with the same ID.
     */
    fun save(ad: AdDocument): AdDocument {
        val doc = mapToDocument(ad)
        val filter = Filters.eq(AdDocument.FIELD_ID, ad.id)
        // Use replaceOne with upsert=true to handle both insert and update based on _id
        collection.replaceOne(filter, doc, ReplaceOptions().upsert(true))
        return ad
    }

    /**
     * Finds ads based on filters, sorting, and pagination.
     */
    fun find(filters: AdFilters, sorting: AdSorting, pageRequest: Pageable): List<AdDocument> {
        val queryFilters = mutableListOf<Bson>()

        // Add status filter - typically only fetch ACTIVE ads unless specified otherwise
        // FetchAdsController implies fetching ACTIVE ads for the general search
        queryFilters.add(Filters.eq(AdDocument.FIELD_STATUS, AdStatus.ACTIVE.name))

        // Filter by user ID if provided (e.g., for "my ads")
        filters.createdBy?.let {
            queryFilters.add(Filters.eq(AdDocument.FIELD_USER_ID, ObjectId(it)))
        }

        // Filter by text (requires a text index on relevant fields)
        // Simple implementation: Check if text filter exists
        // A proper implementation would use Filters.text() and require a text index
        filters.text?.let {
             // Placeholder: Add Filters.text(it) if a text index is configured
             // queryFilters.add(Filters.text(it))
             // Simple alternative (less efficient): Use regex on title/description
             val regexFilter = Filters.or(
                 Filters.regex(AdDocument.FIELD_TITLE, ".*$it.*", "i"), // Case-insensitive regex
                 Filters.regex(AdDocument.FIELD_DESCRIPTION, ".*$it.*", "i")
             )
            queryFilters.add(regexFilter)
        }

        // Filter by price range
        filters.priceRange?.let { range ->
            val priceFilters = mutableListOf<Bson>()
            range.from?.let { priceFilters.add(Filters.gte(AdDocument.FIELD_PRICE, it.toPlainString())) } // Store price as string, compare as needed or use Decimal128
            range.to?.let { priceFilters.add(Filters.lte(AdDocument.FIELD_PRICE, it.toPlainString())) }
            if (priceFilters.isNotEmpty()) {
                queryFilters.add(Filters.and(priceFilters))
            }
        }

        // Filter by location
        filters.location?.let { locationFilter ->
            locationFilter.cityIds?.takeIf { it.isNotEmpty() }?.let {
                queryFilters.add(Filters.`in`(AdDocument.FIELD_CITY_ID, it))
            }
            locationFilter.circle?.let { circle ->
                // Use $geoWithin with $centerSphere for distance queries (requires 2dsphere index)
                // Circle object from Spring Data Geo holds center (Point) and radius (Distance/double)
                // MongoDB $centerSphere expects: [ [ lon, lat ], radius_in_radians ]
                queryFilters.add(
                    Filters.geoWithinCenterSphere(
                        AdDocument.FIELD_LOCATION,
                        circle.center.x, // longitude
                        circle.center.y, // latitude
                        circle.radius.value // radius in radians (already converted in DTO)
                    )
                )
            }
        }

        // Combine all filters
        val combinedFilter = if (queryFilters.isEmpty()) Document() else Filters.and(queryFilters)

        // Determine sorting
        val sort = when (sorting.by) {
            By.CREATED_DATE -> if (sorting.direction == Direction.DESC) {
                Sorts.descending(AdDocument.FIELD_CREATED_AT)
            } else {
                Sorts.ascending(AdDocument.FIELD_CREATED_AT)
            }
            // Add other sorting options if needed
        }

        // Apply query, sort, skip, and limit
        val findIterable = collection.find(combinedFilter).sort(sort)

        if (pageRequest.isPaged) {
            findIterable.skip(pageRequest.offset.toInt()).limit(pageRequest.pageSize)
        }

        return findIterable.map { mapToAdDocument(it) }.toList()
    }


    // --- Manual Mapping Functions ---

    private fun mapToDocument(ad: AdDocument): Document {
        val doc = Document()
            .append(AdDocument.FIELD_ID, ad.id)
            .append(AdDocument.FIELD_USER_ID, ad.userId)
            .append(AdDocument.FIELD_TITLE, ad.title)
            .append(AdDocument.FIELD_DESCRIPTION, ad.description)
            .append(AdDocument.FIELD_PRICE, ad.price?.toPlainString())
            .append(AdDocument.FIELD_CURRENCY, ad.currency?.name)
            .append(AdDocument.FIELD_STATUS, ad.status.name)
            .append(AdDocument.FIELD_CREATED_AT, ad.createdAtMicros)
            .append(AdDocument.FIELD_UPDATED_AT, ad.updatedAtMicros)
            .append(AdDocument.FIELD_CITY_ID, ad.cityId) // Map cityId

        // Map GeoJsonPoint to Document format { type: "Point", coordinates: [lon, lat] }
        ad.location?.let { geoPoint ->
            doc.append(
                AdDocument.FIELD_LOCATION,
                Document("type", "Point").append("coordinates", listOf(geoPoint.x, geoPoint.y))
            )
        }

        return doc
    }

    private fun mapToAdDocument(doc: Document): AdDocument {
        val priceString = doc.getString(AdDocument.FIELD_PRICE)
        val currencyString = doc.getString(AdDocument.FIELD_CURRENCY)
        val statusString = doc.getString(AdDocument.FIELD_STATUS)

        // Map location Document back to GeoJsonPoint
        val locationDoc = doc.get(AdDocument.FIELD_LOCATION, Document::class.java)
        val geoPoint = locationDoc?.let {
            val coordinates = it.getList("coordinates", Number::class.java)
            if (coordinates != null && coordinates.size == 2) {
                GeoJsonPoint(coordinates[0].toDouble(), coordinates[1].toDouble()) // lon, lat
            } else {
                null
            }
        }

        return AdDocument(
            id = doc.getObjectId(AdDocument.FIELD_ID),
            userId = doc.getObjectId(AdDocument.FIELD_USER_ID),
            title = doc.getString(AdDocument.FIELD_TITLE),
            description = doc.getString(AdDocument.FIELD_DESCRIPTION),
            price = priceString?.let { BigDecimal(it) },
            currency = currencyString?.let { Currency.valueOf(it) },
            status = AdStatus.valueOf(statusString ?: AdStatus.INACTIVE.name),
            createdAtMicros = doc.getLong(AdDocument.FIELD_CREATED_AT),
            updatedAtMicros = doc.getLong(AdDocument.FIELD_UPDATED_AT),
            cityId = doc.getString(AdDocument.FIELD_CITY_ID), // Map cityId
            location = geoPoint // Map location
        )
    }
}
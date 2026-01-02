package com.gimlee.ads.persistence

import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.Currency
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.common.persistence.mongo.MongoExceptionUtils
import com.mongodb.MongoException
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
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
     * Atomically ensures that the new stock value is not lower than the current locked stock.
     */
    fun save(ad: AdDocument): AdDocument {
        val doc = mapToDocument(ad)
        val filter = Filters.eq(AdDocument.FIELD_ID, ad.id)

        // Atomic check: ensure new stock is >= current locked stock
        val atomicFilter = Filters.and(
            filter,
            Filters.lte(AdDocument.FIELD_LOCKED_STOCK, ad.stock)
        )

        try {
            val result = collection.replaceOne(atomicFilter, doc, ReplaceOptions().upsert(true))
            if (result.matchedCount == 0L && result.upsertedId == null) {
                // If it didn't match and didn't upsert, it means the document exists but lstk > stock.
                // However, with upsert=true, MongoDB might try to insert and fail with DuplicateKey.
                // We handle that in the catch block.
                handleConstraintViolation(ad)
            }
        } catch (e: MongoException) {
            // Check if it's a duplicate key error (code 11000)
            if (MongoExceptionUtils.isDuplicateKeyException(e)) {
                handleConstraintViolation(ad)
            }
            throw e
        }

        return ad
    }

    private fun handleConstraintViolation(ad: AdDocument) {
        val existing = findById(ad.id)
        if (existing != null && existing.lockedStock > ad.stock) {
            throw IllegalStateException("Stock (${ad.stock}) cannot be lower than locked stock (${existing.lockedStock}).")
        }
    }

    /**
     * Finds ads based on filters, sorting, and pagination.
     */
    fun find(filters: AdFilters, sorting: AdSorting, pageRequest: Pageable): Page<AdDocument> { // Return type changed
        val queryFilters = mutableListOf<Bson>()

        queryFilters.add(Filters.eq(AdDocument.FIELD_STATUS, AdStatus.ACTIVE.name))

        filters.createdBy?.let {
            queryFilters.add(Filters.eq(AdDocument.FIELD_USER_ID, ObjectId(it)))
        }
        filters.text?.let {
            val regexFilter = Filters.or(
                Filters.regex(AdDocument.FIELD_TITLE, ".*$it.*", "i"),
                Filters.regex(AdDocument.FIELD_DESCRIPTION, ".*$it.*", "i")
            )
            queryFilters.add(regexFilter)
        }
        filters.priceRange?.let { range ->
            val priceFilters = mutableListOf<Bson>()
            range.from?.let { priceFilters.add(Filters.gte(AdDocument.FIELD_PRICE, it.toPlainString())) }
            range.to?.let { priceFilters.add(Filters.lte(AdDocument.FIELD_PRICE, it.toPlainString())) }
            if (priceFilters.isNotEmpty()) {
                queryFilters.add(Filters.and(priceFilters))
            }
        }
        filters.location?.let { locationFilter ->
            locationFilter.cityIds?.takeIf { it.isNotEmpty() }?.let {
                queryFilters.add(Filters.`in`(AdDocument.FIELD_CITY_ID, it))
            }
            locationFilter.circle?.let { circle ->
                queryFilters.add(
                    Filters.geoWithinCenterSphere(
                        AdDocument.FIELD_LOCATION,
                        circle.center.x,
                        circle.center.y,
                        circle.radius.value
                    )
                )
            }
        }

        val combinedFilter = if (queryFilters.isEmpty()) Document() else Filters.and(queryFilters)

        val sort = when (sorting.by) {
            By.CREATED_DATE -> if (sorting.direction == Direction.DESC) {
                Sorts.descending(AdDocument.FIELD_CREATED_AT)
            } else {
                Sorts.ascending(AdDocument.FIELD_CREATED_AT)
            }
        }

        val documents: List<AdDocument>
        val total: Long

        if (pageRequest.isPaged) {
            total = collection.countDocuments(combinedFilter)
            val findIterable = collection.find(combinedFilter)
                .sort(sort)
                .skip(pageRequest.offset.toInt())
                .limit(pageRequest.pageSize)
            documents = findIterable.map { mapToAdDocument(it) }.toList()
        } else { // Handles Pageable.unpaged()
            val findIterable = collection.find(combinedFilter).sort(sort)
            documents = findIterable.map { mapToAdDocument(it) }.toList()
            total = documents.size.toLong()
        }

        return PageImpl(documents, pageRequest, total)
    }

    /**
     * Increments the locked stock count by the specified quantity.
     */
    fun incrementLockedStock(adId: ObjectId, quantity: Int = 1) {
        val filter = Filters.and(
            Filters.eq(AdDocument.FIELD_ID, adId),
            Filters.expr(Document("\$lte", listOf(Document("\$add", listOf("$${AdDocument.FIELD_LOCKED_STOCK}", quantity)), "$${AdDocument.FIELD_STOCK}")))
        )
        val update = Updates.inc(AdDocument.FIELD_LOCKED_STOCK, quantity)
        val result = collection.updateOne(filter, update)

        if (result.matchedCount == 0L) {
            val existing = findById(adId)
            if (existing != null && existing.lockedStock + quantity > existing.stock) {
                throw IllegalStateException("Cannot lock more stock. Stock: ${existing.stock}, Current Locked: ${existing.lockedStock}, Requested: $quantity.")
            }
        }
    }

    /**
     * Decrements the locked stock count by the specified quantity.
     */
    fun decrementLockedStock(adId: ObjectId, quantity: Int = 1) {
        val filter = Filters.eq(AdDocument.FIELD_ID, adId)
        val update = Updates.inc(AdDocument.FIELD_LOCKED_STOCK, -quantity)
        collection.updateOne(filter, update)
    }

    /**
     * Decrements both stock and locked stock by the specified quantity (used when purchase is complete).
     */
    fun decrementStockAndLockedStock(adId: ObjectId, quantity: Int = 1) {
        val filter = Filters.eq(AdDocument.FIELD_ID, adId)
        val update = Updates.combine(
            Updates.inc(AdDocument.FIELD_STOCK, -quantity),
            Updates.inc(AdDocument.FIELD_LOCKED_STOCK, -quantity)
        )
        collection.updateOne(filter, update)
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
            .append(AdDocument.FIELD_MEDIA_PATHS, ad.mediaPaths)
            .append(AdDocument.FIELD_MAIN_PHOTO_PATH, ad.mainPhotoPath)
            .append(AdDocument.FIELD_STOCK, ad.stock)
            .append(AdDocument.FIELD_LOCKED_STOCK, ad.lockedStock)


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

        @Suppress("UNCHECKED_CAST")
        val mediaPathsList = doc.get(AdDocument.FIELD_MEDIA_PATHS) as? List<String>

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
            cityId = doc.getString(AdDocument.FIELD_CITY_ID),
            location = geoPoint,
            mediaPaths = mediaPathsList ?: emptyList(),
            mainPhotoPath = doc.getString(AdDocument.FIELD_MAIN_PHOTO_PATH),
            stock = doc.getInteger(AdDocument.FIELD_STOCK) ?: 0,
            lockedStock = doc.getInteger(AdDocument.FIELD_LOCKED_STOCK) ?: 0
        )
    }
}
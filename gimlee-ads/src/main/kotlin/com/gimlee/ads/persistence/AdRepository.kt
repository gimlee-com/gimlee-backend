package com.gimlee.ads.persistence

import com.gimlee.ads.domain.model.AdFilters
import com.gimlee.ads.domain.model.AdSorting
import com.gimlee.ads.domain.model.AdStatus
import com.gimlee.ads.domain.model.By
import com.gimlee.ads.domain.model.Direction
import com.gimlee.ads.domain.model.PricingMode
import com.gimlee.ads.persistence.model.AdDocument
import com.gimlee.common.domain.model.Currency
import com.gimlee.common.persistence.mongo.MongoExceptionUtils
import com.mongodb.MongoException
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.conversions.Bson
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.geo.GeoJsonPoint
import org.springframework.stereotype.Repository

@Repository
class AdRepository(mongoDatabase: MongoDatabase) {

    class AdConcurrentModificationException : IllegalStateException("Ad was modified concurrently")

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
     * Finds multiple AdDocuments by their IDs.
     */
    fun findAllByIds(ids: List<ObjectId>): List<AdDocument> {
        if (ids.isEmpty()) return emptyList()
        val query = Filters.`in`(AdDocument.FIELD_ID, ids)
        return collection.find(query).map { mapToAdDocument(it) }.toList()
    }

    /**
     * Saves a new AdDocument or replaces an existing one with the same ID.
     * Atomically ensures that the new stock value is not lower than the current locked stock.
     */
    fun save(ad: AdDocument): AdDocument {
        val savedDoc = ad.copy(ver = ad.ver + 1)
        val doc = mapToDocument(savedDoc)

        val atomicFilter = Filters.and(
            Filters.eq(AdDocument.FIELD_ID, ad.id),
            Filters.lte(AdDocument.FIELD_LOCKED_STOCK, ad.stock),
            Filters.eq(AdDocument.FIELD_VERSION, ad.ver)
        )

        try {
            val result = collection.replaceOne(atomicFilter, doc)
            if (result.matchedCount == 0L) {
                // No match: either new document or constraint violation
                if (findById(ad.id) == null) {
                    collection.insertOne(doc)
                } else {
                    handleConstraintViolation(ad)
                }
            }
        } catch (e: MongoException) {
            if (MongoExceptionUtils.isDuplicateKeyException(e)) {
                handleConstraintViolation(ad)
            }
            throw e
        }

        return savedDoc
    }

    private fun handleConstraintViolation(ad: AdDocument) {
        val existing = findById(ad.id)
        if (existing != null) {
            if (existing.ver != ad.ver) {
                throw AdConcurrentModificationException()
            }
            if (existing.lockedStock > ad.stock) {
                throw IllegalStateException("Stock (${ad.stock}) cannot be lower than locked stock (${existing.lockedStock}).")
            }
        }
    }

    /**
     * Finds ads based on filters, sorting, and pagination.
     */
    fun find(filters: AdFilters, sorting: AdSorting, pageRequest: Pageable): Page<AdDocument> { // Return type changed
        val queryFilters = mutableListOf<Bson>()

        val statuses = filters.statuses ?: listOf(AdStatus.ACTIVE)
        queryFilters.add(Filters.`in`(AdDocument.FIELD_STATUS, statuses.map { it.name }))

        filters.createdBy?.let {
            queryFilters.add(Filters.eq(AdDocument.FIELD_USER_ID, ObjectId(it)))
        }
        filters.excludeId?.let {
            queryFilters.add(Filters.ne(AdDocument.FIELD_ID, ObjectId(it)))
        }
        filters.categoryId?.let { intId ->
            queryFilters.add(Filters.eq(AdDocument.FIELD_CATEGORY_IDS, intId))
        }
        filters.text?.let {
            val regexFilter = Filters.or(
                Filters.regex(AdDocument.FIELD_TITLE, ".*$it.*", "i"),
                Filters.regex(AdDocument.FIELD_DESCRIPTION, ".*$it.*", "i")
            )
            queryFilters.add(regexFilter)
        }
        if (filters.priceRanges != null) {
            val currencyFilters = filters.priceRanges.map { (currency, range) ->
                val conditions = mutableListOf<Bson>()
                conditions.add(Filters.eq(AdDocument.FIELD_CURRENCY, currency.name))
                range.from?.let { conditions.add(Filters.gte(AdDocument.FIELD_PRICE, Decimal128(it))) }
                range.to?.let { conditions.add(Filters.lte(AdDocument.FIELD_PRICE, Decimal128(it))) }
                Filters.and(conditions)
            }
            if (currencyFilters.isNotEmpty()) {
                queryFilters.add(Filters.or(currencyFilters))
            }
        } else if (filters.priceRange != null) {
            val range = filters.priceRange
            val priceFilters = mutableListOf<Bson>()
            range.from?.let { priceFilters.add(Filters.gte(AdDocument.FIELD_PRICE, Decimal128(it))) }
            range.to?.let { priceFilters.add(Filters.lte(AdDocument.FIELD_PRICE, Decimal128(it))) }
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
        val update = Updates.combine(
            Updates.inc(AdDocument.FIELD_LOCKED_STOCK, quantity),
            Updates.inc(AdDocument.FIELD_VERSION, 1L)
        )
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
        val update = Updates.combine(
            Updates.inc(AdDocument.FIELD_LOCKED_STOCK, -quantity),
            Updates.inc(AdDocument.FIELD_VERSION, 1L)
        )
        collection.updateOne(filter, update)
    }

    /**
     * Decrements both stock and locked stock by the specified quantity (used when purchase is complete).
     * Atomically deactivates the ad (sets status to INACTIVE) if the resulting stock is 0 or less.
     * Returns the AdDocument BEFORE the update.
     */
    fun decrementStockAndLockedStock(adId: ObjectId, quantity: Int = 1): AdDocument? {
        val filter = Filters.eq(AdDocument.FIELD_ID, adId)

        // Using aggregation pipeline update for atomic conditional status change
        val pipeline = listOf(
            Updates.set(AdDocument.FIELD_STOCK, Document("\$subtract", listOf("\$${AdDocument.FIELD_STOCK}", quantity))),
            Updates.set(AdDocument.FIELD_LOCKED_STOCK, Document("\$subtract", listOf("\$${AdDocument.FIELD_LOCKED_STOCK}", quantity))),
            Updates.set(AdDocument.FIELD_STATUS,
                Document("\$cond", listOf(
                    Document("\$lte", listOf("\$${AdDocument.FIELD_STOCK}", 0)),
                    AdStatus.INACTIVE.name,
                    "\$${AdDocument.FIELD_STATUS}"
                ))
            ),
            Updates.set(AdDocument.FIELD_VERSION, Document("\$add", listOf("\$${AdDocument.FIELD_VERSION}", 1L)))
        )
        return collection.findOneAndUpdate(filter, pipeline, FindOneAndUpdateOptions().returnDocument(ReturnDocument.BEFORE))
            ?.let { mapToAdDocument(it) }
    }

    /**
     * Calculates the total active ads per category (including all ancestors).
     */
    fun countAdsPerCategory(): Map<Int, Long> {
        val pipeline = listOf(
            Aggregates.match(Filters.eq(AdDocument.FIELD_STATUS, AdStatus.ACTIVE.name)),
            Aggregates.unwind("\$${AdDocument.FIELD_CATEGORY_IDS}"),
            Aggregates.group("\$${AdDocument.FIELD_CATEGORY_IDS}", Accumulators.sum("count", 1L))
        )
        return collection.aggregate(pipeline)
            .map { 
                val id = it.getInteger("_id")
                val count = (it.get("count") as Number).toLong()
                id to count 
            }
            .toList().toMap()
    }

    fun clear() {
        collection.deleteMany(Document())
    }

    fun countActiveAdsByCategoryIds(categoryIds: List<Int>, limit: Long = 0): Long {
        if (categoryIds.isEmpty()) return 0
        val filter = Filters.and(
            Filters.eq(AdDocument.FIELD_STATUS, AdStatus.ACTIVE.name),
            Filters.`in`(AdDocument.FIELD_CATEGORY_IDS, categoryIds)
        )
        if (limit > 0) {
            return collection.countDocuments(filter, com.mongodb.client.model.CountOptions().limit(limit.toInt()))
        }
        return collection.countDocuments(filter)
    }

    fun deactivateAdsByCategoryIds(categoryIds: List<Int>, now: Long): List<AdDocument> {
        if (categoryIds.isEmpty()) return emptyList()
        val filter = Filters.and(
            Filters.eq(AdDocument.FIELD_STATUS, AdStatus.ACTIVE.name),
            Filters.`in`(AdDocument.FIELD_CATEGORY_IDS, categoryIds)
        )
        val affectedAds = collection.find(filter).map { mapToAdDocument(it) }.toList()
        if (affectedAds.isEmpty()) return emptyList()

        collection.updateMany(
            filter,
            Updates.combine(
                Updates.set(AdDocument.FIELD_STATUS, AdStatus.INACTIVE.name),
                Updates.set(AdDocument.FIELD_UPDATED_AT, now)
            )
        )
        return affectedAds
    }

    fun deactivateAdsByUserId(userId: ObjectId, now: Long): List<AdDocument> {
        val filter = Filters.and(
            Filters.eq(AdDocument.FIELD_STATUS, AdStatus.ACTIVE.name),
            Filters.eq(AdDocument.FIELD_USER_ID, userId)
        )
        val affectedAds = collection.find(filter).map { mapToAdDocument(it) }.toList()
        if (affectedAds.isEmpty()) return emptyList()

        collection.updateMany(
            filter,
            Updates.combine(
                Updates.set(AdDocument.FIELD_STATUS, AdStatus.INACTIVE.name),
                Updates.set(AdDocument.FIELD_UPDATED_AT, now)
            )
        )
        return affectedAds
    }

    fun countByUserId(userId: ObjectId): Long {
        return collection.countDocuments(Filters.eq(AdDocument.FIELD_USER_ID, userId))
    }

    fun countByUserIdAndStatus(userId: ObjectId, status: AdStatus): Long {
        return collection.countDocuments(Filters.and(
            Filters.eq(AdDocument.FIELD_USER_ID, userId),
            Filters.eq(AdDocument.FIELD_STATUS, status.name)
        ))
    }

    fun updateCategoryPathBulk(oldPathPrefix: List<Int>, newPathPrefix: List<Int>) {
        if (oldPathPrefix.isEmpty()) return
        val leafCategoryId = oldPathPrefix.last()
        val affectedAds = collection.find(
            Filters.eq(AdDocument.FIELD_CATEGORY_IDS, leafCategoryId)
        )

        affectedAds.forEach { doc ->
            val currentPath = doc.getList(AdDocument.FIELD_CATEGORY_IDS, Integer::class.java)
                ?.map { it.toInt() } ?: return@forEach
            val oldPrefixSize = oldPathPrefix.size
            if (currentPath.size >= oldPrefixSize && currentPath.subList(0, oldPrefixSize) == oldPathPrefix) {
                val newPath = newPathPrefix + currentPath.subList(oldPrefixSize, currentPath.size)
                collection.updateOne(
                    Filters.eq(AdDocument.FIELD_ID, doc.get(AdDocument.FIELD_ID)),
                    Updates.set(AdDocument.FIELD_CATEGORY_IDS, newPath)
                )
            }
        }
    }

    // --- Manual Mapping Functions ---

    private fun mapToDocument(ad: AdDocument): Document {
        val doc = Document()
            .append(AdDocument.FIELD_ID, ad.id)
            .append(AdDocument.FIELD_USER_ID, ad.userId)
            .append(AdDocument.FIELD_TITLE, ad.title)
            .append(AdDocument.FIELD_DESCRIPTION, ad.description)
            .append(AdDocument.FIELD_PRICING_MODE, ad.pricingMode.shortName)
            .append(AdDocument.FIELD_PRICE, ad.price?.let { Decimal128(it) })
            .append(AdDocument.FIELD_CURRENCY, ad.currency?.name)
            .append(AdDocument.FIELD_SETTLEMENT_CURRENCIES, ad.settlementCurrencies.map { it.name })
            .append(AdDocument.FIELD_STATUS, ad.status.name)
            .append(AdDocument.FIELD_CREATED_AT, ad.createdAtMicros)
            .append(AdDocument.FIELD_UPDATED_AT, ad.updatedAtMicros)
            .append(AdDocument.FIELD_CITY_ID, ad.cityId) // Map cityId
            .append(AdDocument.FIELD_CATEGORY_IDS, ad.categoryIds)
            .append(AdDocument.FIELD_MEDIA_PATHS, ad.mediaPaths)
            .append(AdDocument.FIELD_MAIN_PHOTO_PATH, ad.mainPhotoPath)
            .append(AdDocument.FIELD_STOCK, ad.stock)
            .append(AdDocument.FIELD_LOCKED_STOCK, ad.lockedStock)
            .append(AdDocument.FIELD_VOLATILITY_PROTECTION, ad.volatilityProtection)
            .append(AdDocument.FIELD_VERSION, ad.ver)


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
        val price = doc.get(AdDocument.FIELD_PRICE, Decimal128::class.java)?.bigDecimalValue()
        val currencyString = doc.getString(AdDocument.FIELD_CURRENCY)
        val statusString = doc.getString(AdDocument.FIELD_STATUS)
        val pricingModeString = doc.getString(AdDocument.FIELD_PRICING_MODE)

        @Suppress("UNCHECKED_CAST")
        val settlementCurrencyStrings = doc.get(AdDocument.FIELD_SETTLEMENT_CURRENCIES) as? List<String> ?: emptyList()

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
            pricingMode = pricingModeString?.let { PricingMode.fromShortName(it) } ?: PricingMode.FIXED_CRYPTO,
            price = price,
            currency = currencyString?.let { Currency.valueOf(it)},
            settlementCurrencies = settlementCurrencyStrings.map { Currency.valueOf(it) }.toSet(),
            status = AdStatus.valueOf(statusString ?: AdStatus.INACTIVE.name),
            createdAtMicros = doc.getLong(AdDocument.FIELD_CREATED_AT),
            updatedAtMicros = doc.getLong(AdDocument.FIELD_UPDATED_AT),
            cityId = doc.getString(AdDocument.FIELD_CITY_ID),
            categoryIds = doc.getList(AdDocument.FIELD_CATEGORY_IDS, Integer::class.java)?.map { it.toInt() },
            location = geoPoint,
            mediaPaths = mediaPathsList ?: emptyList(),
            mainPhotoPath = doc.getString(AdDocument.FIELD_MAIN_PHOTO_PATH),
            stock = doc.getInteger(AdDocument.FIELD_STOCK) ?: 0,
            lockedStock = doc.getInteger(AdDocument.FIELD_LOCKED_STOCK) ?: 0,
            volatilityProtection = doc.getBoolean(AdDocument.FIELD_VOLATILITY_PROTECTION) ?: false,
            ver = (doc.get(AdDocument.FIELD_VERSION) as? Number)?.toLong() ?: 0L
        )
    }
}
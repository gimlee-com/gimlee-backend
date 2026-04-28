package com.gimlee.purchases.persistence
import com.gimlee.common.domain.model.Currency

import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.domain.model.PurchaseItem
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.domain.model.PurchaseFilters
import com.gimlee.purchases.domain.model.PurchaseSorting
import com.gimlee.purchases.domain.model.PurchaseSortDirection
import com.gimlee.purchases.domain.model.StatusChange
import com.gimlee.purchases.domain.model.DeliveryAddressSnapshot
import com.gimlee.purchases.persistence.model.PurchaseDocument
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_BUYER_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_CREATED_AT
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_DELIVERY_ADDRESS
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_ITEMS
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_SELLER_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_STATUS
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_STATUS_HISTORY
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_TOTAL_AMOUNT
import com.gimlee.purchases.persistence.model.PurchaseItemDocument
import com.gimlee.purchases.persistence.model.StatusChangeDocument
import com.gimlee.purchases.persistence.model.DeliveryAddressSnapshotDocument
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import org.bson.Document
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class PurchaseRepository(
    private val mongoDatabase: MongoDatabase
) {
    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-purchases"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-purchases"

        private val EXCLUDED_ORDER_COUNT_STATUSES = listOf(
            PurchaseStatus.CANCELLED.id,
            PurchaseStatus.FAILED_PAYMENT_TIMEOUT.id,
            PurchaseStatus.FAILED_PAYMENT_UNDERPAID.id
        )
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(purchase: Purchase): Purchase {
        val doc = purchase.toDocument()
        val bson = mapToBsonDocument(doc)
        val filter = Filters.eq(FIELD_ID, purchase.id)
        val options = com.mongodb.client.model.ReplaceOptions().upsert(true)
        collection.replaceOne(filter, bson, options)
        return purchase
    }

    /**
     * Atomically transitions a purchase to a new status and appends to status history.
     * Only succeeds if the current status is one of [allowedCurrentStatuses].
     * Returns true if the update was applied, false if the precondition was not met.
     */
    fun transitionStatus(
        purchaseId: ObjectId,
        newStatus: PurchaseStatus,
        allowedCurrentStatuses: List<PurchaseStatus>,
        timestamp: Instant
    ): Boolean {
        val filter = Filters.and(
            Filters.eq(FIELD_ID, purchaseId),
            Filters.`in`(FIELD_STATUS, allowedCurrentStatuses.map { it.id })
        )
        val historyEntry = Document()
            .append(StatusChangeDocument.FIELD_STATUS, newStatus.id)
            .append(StatusChangeDocument.FIELD_TIMESTAMP, timestamp.toMicros())

        val update = Updates.combine(
            Updates.set(FIELD_STATUS, newStatus.id),
            Updates.push(FIELD_STATUS_HISTORY, historyEntry)
        )
        return collection.updateOne(filter, update).modifiedCount == 1L
    }

    fun findById(id: ObjectId): Purchase? {
        val filter = Filters.eq(FIELD_ID, id)
        return collection.find(filter).firstOrNull()?.toPurchase()
    }

    fun findAllBySellerId(sellerId: ObjectId, pageable: Pageable): Page<Purchase> {
        return findByField(FIELD_SELLER_ID, sellerId, pageable)
    }

    fun findAllByBuyerId(buyerId: ObjectId, pageable: Pageable): Page<Purchase> {
        return findByField(FIELD_BUYER_ID, buyerId, pageable)
    }

    fun findAllBySellerId(sellerId: ObjectId, filters: PurchaseFilters, sorting: PurchaseSorting, pageable: Pageable): Page<Purchase> {
        return findFiltered(FIELD_SELLER_ID, sellerId, filters, sorting, pageable)
    }

    fun findAllByBuyerId(buyerId: ObjectId, filters: PurchaseFilters, sorting: PurchaseSorting, pageable: Pageable): Page<Purchase> {
        return findFiltered(FIELD_BUYER_ID, buyerId, filters, sorting, pageable)
    }

    private fun findFiltered(
        ownerField: String,
        ownerId: ObjectId,
        filters: PurchaseFilters,
        sorting: PurchaseSorting,
        pageable: Pageable
    ): Page<Purchase> {
        if (filters.noResults) {
            return PageImpl(emptyList(), pageable, 0)
        }

        val conditions = mutableListOf(Filters.eq(ownerField, ownerId))

        filters.purchaseId?.let {
            conditions.add(Filters.eq(FIELD_ID, it))
        }
        filters.statuses?.takeIf { it.isNotEmpty() }?.let {
            conditions.add(Filters.`in`(FIELD_STATUS, it.map { s -> s.id }))
        }
        filters.adId?.let {
            conditions.add(Filters.eq("$FIELD_ITEMS.${PurchaseItemDocument.FIELD_AD_ID}", it))
        }
        filters.fromMicros?.let {
            conditions.add(Filters.gte(FIELD_CREATED_AT, it))
        }
        filters.toMicros?.let {
            conditions.add(Filters.lte(FIELD_CREATED_AT, it))
        }
        filters.buyerIds?.takeIf { it.isNotEmpty() }?.let {
            conditions.add(Filters.`in`(FIELD_BUYER_ID, it))
        }
        filters.sellerIds?.takeIf { it.isNotEmpty() }?.let {
            conditions.add(Filters.`in`(FIELD_SELLER_ID, it))
        }

        val filter = Filters.and(conditions)
        val sort = when (sorting.direction) {
            PurchaseSortDirection.ASC -> Sorts.ascending(sorting.by.mongoField)
            PurchaseSortDirection.DESC -> Sorts.descending(sorting.by.mongoField)
        }

        val total = collection.countDocuments(filter)
        val purchases = if (pageable.isPaged) {
            collection.find(filter)
                .sort(sort)
                .skip(pageable.offset.toInt())
                .limit(pageable.pageSize)
                .map { it.toPurchase() }
                .toList()
        } else {
            collection.find(filter)
                .sort(sort)
                .map { it.toPurchase() }
                .toList()
        }

        return PageImpl(purchases, pageable, total)
    }

    private fun findByField(fieldName: String, value: Any, pageable: Pageable): Page<Purchase> {
        val filter = Filters.eq(fieldName, value)
        val total = collection.countDocuments(filter)
        val sort = Sorts.descending(FIELD_CREATED_AT)

        val purchases = if (pageable.isPaged) {
            collection.find(filter)
                .sort(sort)
                .skip(pageable.offset.toInt())
                .limit(pageable.pageSize)
                .map { it.toPurchase() }
                .toList()
        } else {
            collection.find(filter)
                .sort(sort)
                .map { it.toPurchase() }
                .toList()
        }

        return PageImpl(purchases, pageable, total)
    }

    fun findAllByStatus(status: PurchaseStatus): List<Purchase> {
        val filter = Filters.eq(FIELD_STATUS, status.id)
        return collection.find(filter).map { it.toPurchase() }.toList()
    }

    fun existsByBuyerIdAndAdIdAndStatus(buyerId: ObjectId, adId: ObjectId, status: PurchaseStatus): Boolean {
        val filter = Filters.and(
            Filters.eq(FIELD_BUYER_ID, buyerId),
            Filters.eq(FIELD_STATUS, status.id),
            Filters.eq("${FIELD_ITEMS}.${PurchaseItemDocument.FIELD_AD_ID}", adId)
        )
        return collection.countDocuments(filter) > 0
    }

    fun countByBuyerId(buyerId: ObjectId): Long {
        return collection.countDocuments(Filters.eq(FIELD_BUYER_ID, buyerId))
    }

    fun countBySellerId(sellerId: ObjectId): Long {
        return collection.countDocuments(Filters.eq(FIELD_SELLER_ID, sellerId))
    }

    fun countByBuyerIdAndStatus(buyerId: ObjectId, status: PurchaseStatus): Long {
        return collection.countDocuments(Filters.and(
            Filters.eq(FIELD_BUYER_ID, buyerId),
            Filters.eq(FIELD_STATUS, status.id)
        ))
    }

    fun countBySellerIdAndStatus(sellerId: ObjectId, status: PurchaseStatus): Long {
        return collection.countDocuments(Filters.and(
            Filters.eq(FIELD_SELLER_ID, sellerId),
            Filters.eq(FIELD_STATUS, status.id)
        ))
    }

    /**
     * Counts distinct purchases per ad ID. Only counts purchases in non-terminal-failure statuses.
     * Uses aggregation to deduplicate by (purchaseId, adId) so multi-item purchases aren't double-counted.
     */
    fun countOrdersByAdIds(adIds: List<ObjectId>): Map<ObjectId, Long> {
        if (adIds.isEmpty()) return emptyMap()

        val pipeline = listOf(
            Aggregates.match(
                Filters.and(
                    Filters.elemMatch(FIELD_ITEMS, Filters.`in`(PurchaseItemDocument.FIELD_AD_ID, adIds)),
                    Filters.nin(FIELD_STATUS, EXCLUDED_ORDER_COUNT_STATUSES)
                )
            ),
            Aggregates.unwind("\$$FIELD_ITEMS"),
            Aggregates.match(Filters.`in`("$FIELD_ITEMS.${PurchaseItemDocument.FIELD_AD_ID}", adIds)),
            // Group by (purchaseId, adId) to deduplicate multi-item purchases
            Aggregates.group(
                Document("pid", "\$$FIELD_ID").append("aid", "\$$FIELD_ITEMS.${PurchaseItemDocument.FIELD_AD_ID}")
            ),
            // Then group by adId to count distinct purchases
            Aggregates.group(
                "\$${FIELD_ID}.aid",
                Accumulators.sum("cnt", 1)
            )
        )

        return collection.aggregate(pipeline).associate { doc ->
            val adId = doc.getObjectId(FIELD_ID)
            val count = (doc.get("cnt") as? Number)?.toLong() ?: 0L
            adId to count
        }
    }

    /**
     * Aggregates revenue (sum of totalAmount) per currency for completed purchases by seller.
     * Optionally filtered by creation date.
     */
    fun aggregateRevenueBySellerId(sellerId: ObjectId, fromMicros: Long? = null): Map<Currency, java.math.BigDecimal> {
        val matchFilters = mutableListOf(
            Filters.eq(FIELD_SELLER_ID, sellerId),
            Filters.eq(FIELD_STATUS, PurchaseStatus.COMPLETE.id)
        )
        if (fromMicros != null) {
            matchFilters.add(Filters.gte(FIELD_CREATED_AT, fromMicros))
        }

        val pipeline = listOf(
            Aggregates.match(Filters.and(matchFilters)),
            Aggregates.unwind("\$$FIELD_ITEMS"),
            Aggregates.group(
                "\$$FIELD_ITEMS.${PurchaseItemDocument.FIELD_CURRENCY}",
                Accumulators.sum("revenue", Document("\$multiply", listOf(
                    "\$$FIELD_ITEMS.${PurchaseItemDocument.FIELD_UNIT_PRICE}",
                    "\$$FIELD_ITEMS.${PurchaseItemDocument.FIELD_QUANTITY}"
                )))
            )
        )

        return collection.aggregate(pipeline).mapNotNull { doc ->
            val currencyStr = doc.getString(FIELD_ID) ?: return@mapNotNull null
            val currency = try { Currency.valueOf(currencyStr) } catch (_: Exception) { return@mapNotNull null }
            val revenue = when (val raw = doc.get("revenue")) {
                is Decimal128 -> raw.bigDecimalValue()
                is Number -> java.math.BigDecimal.valueOf(raw.toDouble())
                else -> return@mapNotNull null
            }
            currency to revenue
        }.toMap()
    }

    private fun Purchase.toDocument(): PurchaseDocument =
        PurchaseDocument(
            id = id,
            buyerId = buyerId,
            sellerId = sellerId,
            items = items.map { it.toDocument() },
            totalAmount = Decimal128(totalAmount),
            status = status.id,
            deliveryAddress = deliveryAddress?.let {
                DeliveryAddressSnapshotDocument(
                    name = it.name,
                    fullName = it.fullName,
                    street = it.street,
                    city = it.city,
                    postalCode = it.postalCode,
                    country = it.country,
                    phoneNumber = it.phoneNumber
                )
            },
            statusHistory = statusHistory.map {
                StatusChangeDocument(status = it.status.id, timestampMicros = it.timestamp.toMicros())
            },
            createdAtMicros = createdAt.toMicros()
        )

    private fun PurchaseItem.toDocument(): PurchaseItemDocument =
        PurchaseItemDocument(
            adId = adId,
            quantity = quantity,
            unitPrice = Decimal128(unitPrice),
            currency = currency.name
        )

    private fun mapToBsonDocument(doc: PurchaseDocument): Document {
        val bson = Document()
            .append(FIELD_ID, doc.id)
            .append(FIELD_BUYER_ID, doc.buyerId)
            .append(FIELD_SELLER_ID, doc.sellerId)
            .append(FIELD_ITEMS, doc.items.map { item ->
                Document()
                    .append(PurchaseItemDocument.FIELD_AD_ID, item.adId)
                    .append(PurchaseItemDocument.FIELD_QUANTITY, item.quantity)
                    .append(PurchaseItemDocument.FIELD_UNIT_PRICE, item.unitPrice)
                    .append(PurchaseItemDocument.FIELD_CURRENCY, item.currency)
            })
            .append(FIELD_TOTAL_AMOUNT, doc.totalAmount)
            .append(FIELD_STATUS, doc.status)
            .append(FIELD_STATUS_HISTORY, doc.statusHistory.map { entry ->
                Document()
                    .append(StatusChangeDocument.FIELD_STATUS, entry.status)
                    .append(StatusChangeDocument.FIELD_TIMESTAMP, entry.timestampMicros)
            })
            .append(FIELD_CREATED_AT, doc.createdAtMicros)

        doc.deliveryAddress?.let { addr ->
            bson.append(FIELD_DELIVERY_ADDRESS, Document()
                .append(DeliveryAddressSnapshotDocument.FIELD_NAME, addr.name)
                .append(DeliveryAddressSnapshotDocument.FIELD_FULL_NAME, addr.fullName)
                .append(DeliveryAddressSnapshotDocument.FIELD_STREET, addr.street)
                .append(DeliveryAddressSnapshotDocument.FIELD_CITY, addr.city)
                .append(DeliveryAddressSnapshotDocument.FIELD_POSTAL_CODE, addr.postalCode)
                .append(DeliveryAddressSnapshotDocument.FIELD_COUNTRY, addr.country)
                .append(DeliveryAddressSnapshotDocument.FIELD_PHONE_NUMBER, addr.phoneNumber)
            )
        }

        return bson
    }

    private fun Document.toPurchase(): Purchase = Purchase(
        id = getObjectId(FIELD_ID),
        buyerId = getObjectId(FIELD_BUYER_ID),
        sellerId = getObjectId(FIELD_SELLER_ID),
        items = getList(FIELD_ITEMS, Document::class.java).map { it.toPurchaseItem() },
        totalAmount = get(FIELD_TOTAL_AMOUNT, Decimal128::class.java).bigDecimalValue(),
        status = PurchaseStatus.entries.first { it.id == getInteger(FIELD_STATUS) },
        deliveryAddress = get(FIELD_DELIVERY_ADDRESS, Document::class.java)?.toDeliveryAddressSnapshot(),
        statusHistory = (getList(FIELD_STATUS_HISTORY, Document::class.java) ?: emptyList()).map { it.toStatusChange() },
        createdAt = fromMicros(getLong(FIELD_CREATED_AT))
    )

    private fun Document.toStatusChange(): StatusChange = StatusChange(
        status = PurchaseStatus.entries.first { it.id == getInteger(StatusChangeDocument.FIELD_STATUS) },
        timestamp = fromMicros(getLong(StatusChangeDocument.FIELD_TIMESTAMP))
    )

    private fun Document.toDeliveryAddressSnapshot(): DeliveryAddressSnapshot = DeliveryAddressSnapshot(
        name = getString(DeliveryAddressSnapshotDocument.FIELD_NAME),
        fullName = getString(DeliveryAddressSnapshotDocument.FIELD_FULL_NAME),
        street = getString(DeliveryAddressSnapshotDocument.FIELD_STREET),
        city = getString(DeliveryAddressSnapshotDocument.FIELD_CITY),
        postalCode = getString(DeliveryAddressSnapshotDocument.FIELD_POSTAL_CODE),
        country = getString(DeliveryAddressSnapshotDocument.FIELD_COUNTRY),
        phoneNumber = getString(DeliveryAddressSnapshotDocument.FIELD_PHONE_NUMBER)
    )

    private fun Document.toPurchaseItem(): PurchaseItem = PurchaseItem(
        adId = getObjectId(PurchaseItemDocument.FIELD_AD_ID),
        quantity = getInteger(PurchaseItemDocument.FIELD_QUANTITY),
        unitPrice = get(PurchaseItemDocument.FIELD_UNIT_PRICE, Decimal128::class.java).bigDecimalValue(),
        currency = Currency.valueOf(getString(PurchaseItemDocument.FIELD_CURRENCY))!!
    )
}

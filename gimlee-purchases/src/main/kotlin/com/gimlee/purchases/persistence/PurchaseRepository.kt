package com.gimlee.purchases.persistence
import com.gimlee.common.domain.model.Currency

import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.domain.model.PurchaseItem
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.persistence.model.PurchaseDocument
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_BUYER_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_CREATED_AT
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_ITEMS
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_SELLER_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_STATUS
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_TOTAL_AMOUNT
import com.gimlee.purchases.persistence.model.PurchaseItemDocument
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import org.bson.Document
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class PurchaseRepository(
    private val mongoDatabase: MongoDatabase
) {
    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-purchases"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-purchases"
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

    private fun Purchase.toDocument(): PurchaseDocument =
        PurchaseDocument(
            id = id,
            buyerId = buyerId,
            sellerId = sellerId,
            items = items.map { it.toDocument() },
            totalAmount = Decimal128(totalAmount),
            status = status.id,
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
        return Document()
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
            .append(FIELD_CREATED_AT, doc.createdAtMicros)
    }

    private fun Document.toPurchase(): Purchase = Purchase(
        id = getObjectId(FIELD_ID),
        buyerId = getObjectId(FIELD_BUYER_ID),
        sellerId = getObjectId(FIELD_SELLER_ID),
        items = getList(FIELD_ITEMS, Document::class.java).map { it.toPurchaseItem() },
        totalAmount = get(FIELD_TOTAL_AMOUNT, Decimal128::class.java).bigDecimalValue(),
        status = PurchaseStatus.entries.first { it.id == getInteger(FIELD_STATUS) },
        createdAt = fromMicros(getLong(FIELD_CREATED_AT))
    )

    private fun Document.toPurchaseItem(): PurchaseItem = PurchaseItem(
        adId = getObjectId(PurchaseItemDocument.FIELD_AD_ID),
        quantity = getInteger(PurchaseItemDocument.FIELD_QUANTITY),
        unitPrice = get(PurchaseItemDocument.FIELD_UNIT_PRICE, Decimal128::class.java).bigDecimalValue(),
        currency = Currency.valueOf(getString(PurchaseItemDocument.FIELD_CURRENCY))!!
    )
}

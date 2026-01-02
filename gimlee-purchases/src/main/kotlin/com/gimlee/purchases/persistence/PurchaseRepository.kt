package com.gimlee.purchases.persistence

import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import com.gimlee.purchases.domain.model.Purchase
import com.gimlee.purchases.domain.model.PurchaseStatus
import com.gimlee.purchases.persistence.model.PurchaseDocument
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_AD_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_AMOUNT
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_BUYER_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_CREATED_AT
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_SELLER_ID
import com.gimlee.purchases.persistence.model.PurchaseDocument.Companion.FIELD_STATUS
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.types.Decimal128
import org.bson.types.ObjectId
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
    
    fun findAllByStatus(status: PurchaseStatus): List<Purchase> {
        val filter = Filters.eq(FIELD_STATUS, status.id)
        return collection.find(filter).map { it.toPurchase() }.toList()
    }

    private fun Purchase.toDocument(): PurchaseDocument =
        PurchaseDocument(
            id = id,
            buyerId = buyerId,
            sellerId = sellerId,
            adId = adId,
            amount = Decimal128(amount),
            status = status.id,
            createdAtMicros = createdAt.toMicros()
        )

    private fun mapToBsonDocument(doc: PurchaseDocument): Document {
        return Document()
            .append(FIELD_ID, doc.id)
            .append(FIELD_BUYER_ID, doc.buyerId)
            .append(FIELD_SELLER_ID, doc.sellerId)
            .append(FIELD_AD_ID, doc.adId)
            .append(FIELD_AMOUNT, doc.amount)
            .append(FIELD_STATUS, doc.status)
            .append(FIELD_CREATED_AT, doc.createdAtMicros)
    }

    private fun Document.toPurchase(): Purchase = Purchase(
        id = getObjectId(FIELD_ID),
        buyerId = getObjectId(FIELD_BUYER_ID),
        sellerId = getObjectId(FIELD_SELLER_ID),
        adId = getObjectId(FIELD_AD_ID),
        amount = get(FIELD_AMOUNT, Decimal128::class.java).bigDecimalValue(),
        status = PurchaseStatus.entries.first { it.id == getInteger(FIELD_STATUS) },
        createdAt = fromMicros(getLong(FIELD_CREATED_AT))
    )
}

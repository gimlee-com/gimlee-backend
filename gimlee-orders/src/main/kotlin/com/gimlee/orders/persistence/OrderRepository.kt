package com.gimlee.orders.persistence

import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import com.gimlee.orders.domain.model.Order
import com.gimlee.orders.domain.model.OrderStatus
import com.gimlee.orders.persistence.model.OrderDocument
import com.gimlee.orders.persistence.model.OrderDocument.Companion.FIELD_AD_ID
import com.gimlee.orders.persistence.model.OrderDocument.Companion.FIELD_AMOUNT
import com.gimlee.orders.persistence.model.OrderDocument.Companion.FIELD_BUYER_ID
import com.gimlee.orders.persistence.model.OrderDocument.Companion.FIELD_CREATED_AT
import com.gimlee.orders.persistence.model.OrderDocument.Companion.FIELD_ID
import com.gimlee.orders.persistence.model.OrderDocument.Companion.FIELD_SELLER_ID
import com.gimlee.orders.persistence.model.OrderDocument.Companion.FIELD_STATUS
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class OrderRepository(
    private val mongoDatabase: MongoDatabase
) {
    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-orders"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-orders"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(order: Order): Order {
        val doc = order.toDocument()
        val bson = mapToBsonDocument(doc)
        val filter = Filters.eq(FIELD_ID, order.id)
        val options = com.mongodb.client.model.ReplaceOptions().upsert(true)
        collection.replaceOne(filter, bson, options)
        return order
    }

    fun findById(id: ObjectId): Order? {
        val filter = Filters.eq(FIELD_ID, id)
        return collection.find(filter).firstOrNull()?.toOrder()
    }
    
    fun findAllByStatus(status: OrderStatus): List<Order> {
        val filter = Filters.eq(FIELD_STATUS, status.id)
        return collection.find(filter).map { it.toOrder() }.toList()
    }

    private fun Order.toDocument(): OrderDocument =
        OrderDocument(
            id = id,
            buyerId = buyerId,
            sellerId = sellerId,
            adId = adId,
            amount = Decimal128(amount),
            status = status.id,
            createdAtMicros = createdAt.toMicros()
        )

    private fun mapToBsonDocument(doc: OrderDocument): Document {
        return Document()
            .append(FIELD_ID, doc.id)
            .append(FIELD_BUYER_ID, doc.buyerId)
            .append(FIELD_SELLER_ID, doc.sellerId)
            .append(FIELD_AD_ID, doc.adId)
            .append(FIELD_AMOUNT, doc.amount)
            .append(FIELD_STATUS, doc.status)
            .append(FIELD_CREATED_AT, doc.createdAtMicros)
    }

    private fun Document.toOrder(): Order = Order(
        id = getObjectId(FIELD_ID),
        buyerId = getObjectId(FIELD_BUYER_ID),
        sellerId = getObjectId(FIELD_SELLER_ID),
        adId = getObjectId(FIELD_AD_ID),
        amount = get(FIELD_AMOUNT, Decimal128::class.java).bigDecimalValue(),
        status = OrderStatus.entries.first { it.id == getInteger(FIELD_STATUS) },
        createdAt = fromMicros(getLong(FIELD_CREATED_AT))
    )
}

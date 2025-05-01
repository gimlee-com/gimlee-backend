package com.gimlee.payments.persistence

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.bson.Document
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository
import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import com.gimlee.events.PaymentEvent
import com.gimlee.payments.persistence.model.PaymentEventDocument
import com.gimlee.payments.persistence.model.PaymentEventDocument.Companion.FIELD_AMOUNT
import com.gimlee.payments.persistence.model.PaymentEventDocument.Companion.FIELD_BUYER_ID
import com.gimlee.payments.persistence.model.PaymentEventDocument.Companion.FIELD_ID
import com.gimlee.payments.persistence.model.PaymentEventDocument.Companion.FIELD_ORDER_ID
import com.gimlee.payments.persistence.model.PaymentEventDocument.Companion.FIELD_PAYMENT_METHOD
import com.gimlee.payments.persistence.model.PaymentEventDocument.Companion.FIELD_SELLER_ID
import com.gimlee.payments.persistence.model.PaymentEventDocument.Companion.FIELD_STATUS
import com.gimlee.payments.persistence.model.PaymentEventDocument.Companion.FIELD_TIMESTAMP

@Repository
class PaymentEventRepository(
    private val mongoDatabase: MongoDatabase
) {
    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-payments"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-events"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    /**
     * Saves a PaymentEvent by converting it to PaymentEventDocument and then to a BSON Document
     * before inserting into the collection.
     *
     * @param paymentEvent The domain event to save.
     * @return The original PaymentEvent that was saved.
     */
    fun save(paymentEvent: PaymentEvent): PaymentEvent {
        val eventDocument = paymentEvent.toDocument()
        val bsonDocument = mapToBsonDocument(eventDocument)
        collection.insertOne(bsonDocument)
        return paymentEvent
    }

    private fun PaymentEvent.toDocument(): PaymentEventDocument =
        PaymentEventDocument(
            id = ObjectId.get(),
            orderId = orderId,
            buyerId = buyerId,       // Map buyerId
            sellerId = sellerId,     // Map sellerId
            status = status,
            paymentMethod = paymentMethod,
            amount = Decimal128(amount), // Use Decimal128 for BigDecimal
            timestampMicros = timestamp.toMicros()
        )

    /**
     * Maps the PaymentEventDocument to a BSON Document for MongoDB insertion.
     */
    private fun mapToBsonDocument(eventDoc: PaymentEventDocument): Document {
        return Document()
            .append(FIELD_ID, eventDoc.id)
            .append(FIELD_ORDER_ID, eventDoc.orderId)
            .append(FIELD_BUYER_ID, eventDoc.buyerId)
            .append(FIELD_SELLER_ID, eventDoc.sellerId)
            .append(FIELD_STATUS, eventDoc.status)
            .append(FIELD_PAYMENT_METHOD, eventDoc.paymentMethod)
            .append(FIELD_AMOUNT, eventDoc.amount)
            .append(FIELD_TIMESTAMP, eventDoc.timestampMicros)
    }

    /**
     * Maps BSON Document to a PaymentEvent.
     */
    private fun Document.toPaymentEvent(): PaymentEvent = PaymentEvent(
        orderId = getObjectId(FIELD_ORDER_ID),
        buyerId = getObjectId(FIELD_BUYER_ID),
        sellerId = getObjectId(FIELD_SELLER_ID),
        status = getInteger(FIELD_STATUS),
        paymentMethod = getInteger(FIELD_PAYMENT_METHOD),
        amount = get(FIELD_AMOUNT, Decimal128::class.java).bigDecimalValue(),
        timestamp = fromMicros(getLong(FIELD_TIMESTAMP))
    )
}
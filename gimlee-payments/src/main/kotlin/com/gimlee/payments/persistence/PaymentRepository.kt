package com.gimlee.payments.persistence

import com.gimlee.common.InstantUtils.fromMicros
import com.gimlee.common.toMicros
import com.gimlee.payments.domain.model.Payment
import com.gimlee.payments.domain.model.PaymentMethod
import com.gimlee.payments.domain.model.PaymentStatus
import com.gimlee.payments.persistence.model.PaymentDocument
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_AMOUNT
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_BUYER_ID
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_CREATED_AT
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_DEADLINE
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_ID
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_MEMO
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_PURCHASE_ID
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_PAYMENT_METHOD
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_RECEIVING_ADDRESS
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_SELLER_ID
import com.gimlee.payments.persistence.model.PaymentDocument.Companion.FIELD_STATUS
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import org.bson.Document
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import org.springframework.stereotype.Repository

@Repository
class PaymentRepository(
    private val mongoDatabase: MongoDatabase
) {
    companion object {
        private const val COLLECTION_NAME_PREFIX = "gimlee-payments"
        const val COLLECTION_NAME = "$COLLECTION_NAME_PREFIX-transactions"
    }

    private val collection: MongoCollection<Document> by lazy {
        mongoDatabase.getCollection(COLLECTION_NAME)
    }

    fun save(payment: Payment): Payment {
        val doc = payment.toDocument()
        val bson = mapToBsonDocument(doc)
        val filter = Filters.eq(FIELD_ID, payment.id)
        val options = com.mongodb.client.model.ReplaceOptions().upsert(true)
        collection.replaceOne(filter, bson, options)
        return payment
    }
    
    fun findById(id: ObjectId): Payment? {
        val filter = Filters.eq(FIELD_ID, id)
        return collection.find(filter).firstOrNull()?.toPayment()
    }

    fun findByPurchaseId(purchaseId: ObjectId): Payment? {
        val filter = Filters.eq(FIELD_PURCHASE_ID, purchaseId)
        return collection.find(filter).firstOrNull()?.toPayment()
    }

    fun findAllByStatus(status: PaymentStatus): List<Payment> {
        val filter = Filters.eq(FIELD_STATUS, status.id)
        return collection.find(filter).map { it.toPayment() }.toList()
    }

    private fun Payment.toDocument(): PaymentDocument =
        PaymentDocument(
            id = id,
            purchaseId = purchaseId,
            buyerId = buyerId,
            sellerId = sellerId,
            amount = Decimal128(amount),
            status = status.id,
            paymentMethod = paymentMethod.id,
            memo = memo,
            deadlineMicros = deadline.toMicros(),
            receivingAddress = receivingAddress,
            createdAtMicros = createdAt.toMicros()
        )

    private fun mapToBsonDocument(doc: PaymentDocument): Document {
        return Document()
            .append(FIELD_ID, doc.id)
            .append(FIELD_PURCHASE_ID, doc.purchaseId)
            .append(FIELD_BUYER_ID, doc.buyerId)
            .append(FIELD_SELLER_ID, doc.sellerId)
            .append(FIELD_AMOUNT, doc.amount)
            .append(FIELD_STATUS, doc.status)
            .append(FIELD_PAYMENT_METHOD, doc.paymentMethod)
            .append(FIELD_MEMO, doc.memo)
            .append(FIELD_DEADLINE, doc.deadlineMicros)
            .append(FIELD_RECEIVING_ADDRESS, doc.receivingAddress)
            .append(FIELD_CREATED_AT, doc.createdAtMicros)
    }

    private fun Document.toPayment(): Payment = Payment(
        id = getObjectId(FIELD_ID),
        purchaseId = getObjectId(FIELD_PURCHASE_ID),
        buyerId = getObjectId(FIELD_BUYER_ID),
        sellerId = getObjectId(FIELD_SELLER_ID),
        amount = get(FIELD_AMOUNT, Decimal128::class.java).bigDecimalValue(),
        status = PaymentStatus.entries.first { it.id == getInteger(FIELD_STATUS) },
        paymentMethod = PaymentMethod.lookupById(getInteger(FIELD_PAYMENT_METHOD)),
        memo = getString(FIELD_MEMO),
        deadline = fromMicros(getLong(FIELD_DEADLINE)),
        receivingAddress = getString(FIELD_RECEIVING_ADDRESS),
        createdAt = fromMicros(getLong(FIELD_CREATED_AT))
    )
}
